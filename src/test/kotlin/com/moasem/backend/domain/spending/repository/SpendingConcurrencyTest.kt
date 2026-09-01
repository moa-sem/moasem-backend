package com.moasem.backend.domain.spending.repository

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.hasErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 승인·반려 중복 처리 방지를 실제 트랜잭션 두 개로 검증한다.
 *
 * 단위 테스트로는 "락 조회 메서드를 호출한다"까지밖에 확인할 수 없다. 두 트랜잭션이 실제로
 * 부딪혔을 때 하나만 통과하는지는 DB가 있어야 알 수 있어서 여기서 확인한다.
 *
 * 테스트 메서드 자체는 트랜잭션 밖에서 돈다([Propagation.NOT_SUPPORTED]). 테스트가 트랜잭션을
 * 잡고 있으면 다른 스레드가 저장된 지출을 볼 수 없기 때문이다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SpendingConcurrencyTest @Autowired constructor(
    private val spendingRepository: SpendingRepository,
    transactionManager: PlatformTransactionManager,
) {

    private val transaction = TransactionTemplate(transactionManager)

    @AfterEach
    fun tearDown() {
        transaction.executeWithoutResult { spendingRepository.deleteAll() }
    }

    @Test
    @DisplayName("같은 지출에 승인이 동시에 둘 들어오면 하나만 통과한다")
    fun concurrentApprovals() {
        val spendingId = givenPendingSpending()

        val results = runConcurrently(
            { approve(spendingId, FIRST_OWNER_ID) },
            { approve(spendingId, SECOND_OWNER_ID) },
        )

        assertThat(results.count { it == null }).isEqualTo(1)
        assertThat(results.filterNotNull()).singleElement()
            .hasErrorCode(ErrorCode.SPENDING_ALREADY_HANDLED)

        val processed = findById(spendingId)
        assertThat(processed.status).isEqualTo(SpendingStatus.APPROVED)
        assertThat(processed.processedByUserId).isIn(FIRST_OWNER_ID, SECOND_OWNER_ID)
    }

    @Test
    @DisplayName("승인과 반려가 동시에 들어와도 하나만 반영된다")
    fun concurrentApprovalAndRejection() {
        val spendingId = givenPendingSpending()

        val results = runConcurrently(
            { approve(spendingId, FIRST_OWNER_ID) },
            { reject(spendingId, SECOND_OWNER_ID) },
        )

        assertThat(results.count { it == null }).isEqualTo(1)

        val processed = findById(spendingId)
        assertThat(processed.status).isIn(SpendingStatus.APPROVED, SpendingStatus.REJECTED)
        // 승인이 이겼으면 반려 사유가 남지 않아야 하고, 반려가 이겼으면 반드시 남아야 한다.
        if (processed.status == SpendingStatus.APPROVED) {
            assertThat(processed.rejectionReason).isNull()
        } else {
            assertThat(processed.rejectionReason).isNotBlank()
        }
    }

    /** 두 작업을 동시에 출발시키고 각자가 던진 예외를 모은다. 통과한 쪽은 null이다. */
    private fun runConcurrently(vararg tasks: () -> Unit): List<Throwable?> {
        val startLine = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(tasks.size)
        try {
            val futures = tasks.map { task ->
                executor.submit<Throwable?> {
                    startLine.await()
                    runCatching(task).exceptionOrNull()
                }
            }
            startLine.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    /** 프로덕션 승인 흐름과 같은 순서로 락을 잡고 상태를 바꾼다. */
    private fun approve(spendingId: Long, processorUserId: Long) = transaction.executeWithoutResult {
        val spending = spendingRepository.findWithLockByIdAndEventId(spendingId, EVENT_ID)
            ?: error("지출을 찾을 수 없습니다.")
        spending.approve(processorUserId)
        spendingRepository.saveAndFlush(spending)
    }

    private fun reject(spendingId: Long, processorUserId: Long) = transaction.executeWithoutResult {
        val spending = spendingRepository.findWithLockByIdAndEventId(spendingId, EVENT_ID)
            ?: error("지출을 찾을 수 없습니다.")
        spending.reject(processorUserId, "증빙이 흐립니다.")
        spendingRepository.saveAndFlush(spending)
    }

    private fun givenPendingSpending(): Long = transaction.execute {
        spendingRepository.saveAndFlush(
            Spending.create(
                eventId = EVENT_ID,
                applicantUserId = APPLICANT_ID,
                amount = 15_000L,
                spentOn = LocalDate.of(2026, 8, 20),
                reason = "1일차 점심 식사",
                tag = SpendingTag.MEAL,
                otherDetail = null,
                evidence = Spending.Evidence(
                    EvidenceType.RECEIPT,
                    "spendings/$EVENT_ID/$APPLICANT_ID/evidence.jpg",
                    "image/jpeg",
                    204_800L,
                ),
            ),
        ).id!!
    }!!

    private fun findById(spendingId: Long): Spending =
        transaction.execute { spendingRepository.findById(spendingId).orElseThrow() }!!

    companion object {
        private const val EVENT_ID = 100L
        private const val APPLICANT_ID = 10L
        private const val FIRST_OWNER_ID = 30L
        private const val SECOND_OWNER_ID = 31L
    }
}
