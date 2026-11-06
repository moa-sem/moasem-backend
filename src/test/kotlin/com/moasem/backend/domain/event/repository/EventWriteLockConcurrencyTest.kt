package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.service.adapter.EventAccessAdapter
import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.repository.SpendingRepository
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Event 행을 공통 락으로 쓰는 변경과 마감의 경쟁을 실제 DB 트랜잭션으로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EventAccessAdapter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventWriteLockConcurrencyTest @Autowired constructor(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val spendingRepository: SpendingRepository,
    private val eventAccessProvider: EventAccessProvider,
    transactionManager: PlatformTransactionManager,
) {

    private val transaction = TransactionTemplate(transactionManager)

    @AfterEach
    fun tearDown() {
        transaction.executeWithoutResult {
            spendingRepository.deleteAll()
            budgetAdditionRepository.deleteAll()
            eventRepository.deleteAll()
        }
    }

    @Test
    @DisplayName("Event 잠금 조회는 기존 트랜잭션 안에서만 실행된다")
    fun eventLockRequiresTransaction() {
        val eventId = givenActiveEvent()

        assertThatThrownBy { eventAccessProvider.findAccessForUpdate(eventId) }
            .isInstanceOf(IllegalTransactionStateException::class.java)

        val access = transaction.execute { eventAccessProvider.findAccessForUpdate(eventId) }
        assertThat(access?.eventId).isEqualTo(eventId)
        assertThat(access?.isActive).isTrue()
    }

    @Test
    @DisplayName("마감과 예산 추가가 경쟁해도 마감 이후의 예산은 남지 않는다")
    fun closeAndAddBudget() {
        val eventId = givenActiveEvent()

        val results = runConcurrently(
            { close(eventId) },
            { addBudget(eventId) },
        )

        assertThat(results[0]).isNull()
        assertSuccessOrError(results[1], ErrorCode.EVENT_ALREADY_CLOSED)

        val event = findEvent(eventId)
        val additions = transaction.execute { budgetAdditionRepository.findAllByEventId(eventId) }!!
        assertThat(event.status).isEqualTo(EventStatus.CLOSED)
        assertThat(additions).hasSize(if (results[1] == null) 1 else 0)
    }

    @Test
    @DisplayName("마감과 지출 신청이 경쟁해도 CLOSED 행사에 PENDING 지출은 남지 않는다")
    fun closeAndCreateSpending() {
        val eventId = givenActiveEvent()

        val results = runConcurrently(
            { close(eventId) },
            { createSpending(eventId) },
        )

        assertThat(results.count { it == null }).isEqualTo(1)
        assertSuccessOrError(results[0], ErrorCode.EVENT_HAS_PENDING_SPENDING)
        assertSuccessOrError(results[1], ErrorCode.EVENT_ALREADY_CLOSED)

        val event = findEvent(eventId)
        val spendings = findSpendings(eventId)
        if (event.status == EventStatus.CLOSED) {
            assertThat(spendings).isEmpty()
        } else {
            assertThat(spendings).singleElement().extracting(Spending::status).isEqualTo(SpendingStatus.PENDING)
            assertThat(event.participantCount).isNull()
            assertThat(event.closedAt).isNull()
        }
    }

    @Test
    @DisplayName("마감과 지출 승인이 경쟁해도 PENDING 지출이 남은 채 마감되지 않는다")
    fun closeAndApproveSpending() {
        val eventId = givenActiveEvent()
        val spendingId = givenPendingSpending(eventId)

        val results = runConcurrently(
            { close(eventId) },
            { approve(eventId, spendingId) },
        )

        assertSuccessOrError(results[0], ErrorCode.EVENT_HAS_PENDING_SPENDING)
        assertThat(results[1]).isNull()

        val event = findEvent(eventId)
        val spending = findSpending(spendingId)
        assertThat(spending.status).isEqualTo(SpendingStatus.APPROVED)
        if (event.status == EventStatus.CLOSED) {
            assertThat(findSpendings(eventId)).noneMatch { it.status == SpendingStatus.PENDING }
        } else {
            assertThat(event.participantCount).isNull()
            assertThat(event.closedAt).isNull()
        }
    }

    @Test
    @DisplayName("마감과 지출 수정이 경쟁하면 수정은 반영되고 PENDING 지출이 있는 마감은 롤백된다")
    fun closeAndUpdateSpending() {
        val eventId = givenActiveEvent()
        val spendingId = givenPendingSpending(eventId)

        val results = runConcurrently(
            { close(eventId) },
            { update(eventId, spendingId) },
        )

        assertError(results[0], ErrorCode.EVENT_HAS_PENDING_SPENDING)
        assertThat(results[1]).isNull()

        val event = findEvent(eventId)
        val spending = findSpending(spendingId)
        assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
        assertThat(event.participantCount).isNull()
        assertThat(event.closedAt).isNull()
        assertThat(spending.status).isEqualTo(SpendingStatus.PENDING)
        assertThat(spending.amount).isEqualTo(UPDATED_AMOUNT)
        assertThat(spending.reason).isEqualTo(UPDATED_REASON)
    }

    /** 두 트랜잭션이 시작된 뒤 같은 Event 락 조회로 동시에 진입하게 한다. */
    private fun runConcurrently(vararg tasks: () -> Unit): List<Throwable?> {
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(tasks.size)
        try {
            val futures = tasks.map { task ->
                executor.submit<Throwable?> {
                    runCatching {
                        transaction.executeWithoutResult {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS)) { "동시성 테스트 시작 대기 시간을 초과했습니다." }
                            task()
                        }
                    }.exceptionOrNull()
                }
            }
            check(ready.await(5, TimeUnit.SECONDS)) { "동시성 테스트 준비 시간을 초과했습니다." }
            start.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun close(eventId: Long) {
        val event = lockEvent(eventId)
        if (event.status != EventStatus.ACTIVE) throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)
        if (spendingRepository.countByEventIdAndStatus(eventId, SpendingStatus.PENDING) != 0L) {
            throw BusinessException(ErrorCode.EVENT_HAS_PENDING_SPENDING)
        }
        event.close(PARTICIPANT_COUNT)
        eventRepository.flush()
    }

    private fun addBudget(eventId: Long) {
        val event = lockEvent(eventId)
        validateActive(event)
        budgetAdditionRepository.saveAndFlush(
            BudgetAddition.create(eventId, ADDED_BUDGET, "참가 인원 증가", OWNER_ID),
        )
    }

    private fun createSpending(eventId: Long) {
        val event = lockEvent(eventId)
        validateActive(event)
        spendingRepository.saveAndFlush(newSpending(eventId))
    }

    private fun approve(eventId: Long, spendingId: Long) {
        val event = lockEvent(eventId)
        validateActive(event)
        val spending = lockSpending(eventId, spendingId)
        spending.approve(OWNER_ID)
        spendingRepository.flush()
    }

    private fun update(eventId: Long, spendingId: Long) {
        val event = lockEvent(eventId)
        validateActive(event)
        val spending = lockSpending(eventId, spendingId)
        spending.update(
            amount = UPDATED_AMOUNT,
            spentOn = LocalDate.of(2026, 8, 21),
            reason = UPDATED_REASON,
            tag = SpendingTag.MEAL,
            otherDetail = null,
            evidence = Spending.Evidence(
                EvidenceType.RECEIPT,
                "spendings/$eventId/$APPLICANT_ID/updated.jpg",
                "image/jpeg",
                102_400L,
            ),
        )
        spendingRepository.flush()
    }

    private fun lockEvent(eventId: Long): Event =
        eventRepository.findByIdAndDeletedAtIsNullForUpdate(eventId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

    private fun lockSpending(eventId: Long, spendingId: Long): Spending =
        spendingRepository.findWithLockByIdAndEventId(spendingId, eventId)
            ?: throw BusinessException(ErrorCode.SPENDING_NOT_FOUND)

    private fun validateActive(event: Event) {
        if (event.status != EventStatus.ACTIVE) throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)
    }

    private fun givenActiveEvent(): Long = transaction.execute {
        eventRepository.saveAndFlush(
            Event.create(
                groupId = GROUP_ID,
                title = "여름 MT",
                description = null,
                startAt = LocalDateTime.of(2026, 8, 28, 10, 0),
                endAt = LocalDateTime.of(2026, 8, 30, 12, 0),
                initialBudget = 500_000L,
            ),
        ).id!!
    }!!

    private fun givenPendingSpending(eventId: Long): Long = transaction.execute {
        spendingRepository.saveAndFlush(newSpending(eventId)).id!!
    }!!

    private fun newSpending(eventId: Long): Spending = Spending.create(
        eventId = eventId,
        applicantUserId = APPLICANT_ID,
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = SpendingTag.MEAL,
        otherDetail = null,
        evidence = Spending.Evidence(
            EvidenceType.RECEIPT,
            "spendings/$eventId/$APPLICANT_ID/evidence.jpg",
            "image/jpeg",
            204_800L,
        ),
    )

    private fun findEvent(eventId: Long): Event =
        transaction.execute { eventRepository.findById(eventId).orElseThrow() }!!

    private fun findSpendings(eventId: Long): List<Spending> =
        transaction.execute { spendingRepository.findAll().filter { it.eventId == eventId } }!!

    private fun findSpending(spendingId: Long): Spending =
        transaction.execute { spendingRepository.findById(spendingId).orElseThrow() }!!

    private fun assertSuccessOrError(actual: Throwable?, errorCode: ErrorCode) {
        if (actual != null) assertError(actual, errorCode)
    }

    private fun assertError(actual: Throwable?, errorCode: ErrorCode) {
        assertThat(actual).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode).isEqualTo(errorCode)
        }
    }

    companion object {
        private const val GROUP_ID = 1L
        private const val OWNER_ID = 10L
        private const val APPLICANT_ID = 20L
        private const val PARTICIPANT_COUNT = 3
        private const val ADDED_BUDGET = 100_000L
        private const val UPDATED_AMOUNT = 30_000L
        private const val UPDATED_REASON = "1일차 저녁 식사"
    }
}
