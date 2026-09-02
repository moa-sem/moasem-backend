package com.moasem.backend.domain.spending.service.adapter

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.repository.SpendingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * event 도메인이 마감 정산에 쓰는 집계값을 검증한다.
 *
 * 이 값이 틀리면 결산 금액이 그대로 틀리고, 스냅샷은 불변이라 되돌릴 수 없다.
 * 특히 "승인 건만 더한다"와 "행사 경계를 넘지 않는다"가 핵심이다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SpendingQueryAdapter::class)
class SpendingQueryAdapterTest @Autowired constructor(
    private val spendingRepository: SpendingRepository,
    private val adapter: SpendingQueryAdapter,
) {

    @Test
    @DisplayName("승인된 지출만 합계에 들어간다")
    fun approvedTotalCountsApprovedOnly() {
        save(amount = 10_000L).also { it.approve(OWNER_ID) }
        save(amount = 5_000L).also { it.approve(OWNER_ID) }
        save(amount = 99_000L)
        save(amount = 77_000L).also { it.reject(OWNER_ID, "증빙 누락") }
        spendingRepository.flush()

        assertThat(adapter.getApprovedSpendingTotal(EVENT_ID)).isEqualTo(15_000L)
    }

    @Test
    @DisplayName("다른 행사의 승인 지출은 합계에 섞이지 않는다")
    fun approvedTotalIsScopedToEvent() {
        save(amount = 10_000L).also { it.approve(OWNER_ID) }
        save(eventId = OTHER_EVENT_ID, amount = 50_000L).also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        assertThat(adapter.getApprovedSpendingTotal(EVENT_ID)).isEqualTo(10_000L)
    }

    @Test
    @DisplayName("승인된 지출이 없으면 합계는 null이 아니라 0이다")
    fun approvedTotalIsZeroWhenNothingApproved() {
        save(amount = 10_000L)

        assertThat(adapter.getApprovedSpendingTotal(EVENT_ID)).isZero()
        assertThat(adapter.getApprovedSpendingTotal(EMPTY_EVENT_ID)).isZero()
    }

    @Test
    @DisplayName("PENDING 건수는 처리되지 않은 신청만 센다")
    fun pendingCountCountsPendingOnly() {
        save()
        save()
        save().also { it.approve(OWNER_ID) }
        save().also { it.reject(OWNER_ID, "증빙 누락") }
        save(eventId = OTHER_EVENT_ID)
        spendingRepository.flush()

        assertThat(adapter.getPendingSpendingCount(EVENT_ID)).isEqualTo(2L)
        assertThat(adapter.getPendingSpendingCount(EMPTY_EVENT_ID)).isZero()
    }

    @Test
    @DisplayName("지출 이력은 상태를 가리지 않는다")
    fun historyIgnoresStatus() {
        save().also { it.reject(OWNER_ID, "증빙 누락") }
        spendingRepository.flush()

        assertThat(adapter.hasAnySpending(EVENT_ID)).isTrue()
        assertThat(adapter.hasAnySpending(EMPTY_EVENT_ID)).isFalse()
    }

    private fun save(
        eventId: Long = EVENT_ID,
        amount: Long = 15_000L,
    ): Spending = spendingRepository.save(
        Spending.create(
            eventId = eventId,
            applicantUserId = APPLICANT_ID,
            amount = amount,
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
        ),
    )

    companion object {
        private const val EVENT_ID = 100L
        private const val OTHER_EVENT_ID = 200L
        private const val EMPTY_EVENT_ID = 300L
        private const val APPLICANT_ID = 10L
        private const val OWNER_ID = 30L
    }
}
