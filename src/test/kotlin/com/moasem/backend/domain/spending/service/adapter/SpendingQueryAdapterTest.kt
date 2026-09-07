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
    @DisplayName("승인된 지출만 목록에 들어간다")
    fun approvedListContainsApprovedOnly() {
        val approved = save(amount = 10_000L).also { it.approve(OWNER_ID) }
        save(amount = 99_000L)
        save(amount = 77_000L).also { it.reject(OWNER_ID, "증빙 누락") }
        save(eventId = OTHER_EVENT_ID, amount = 50_000L).also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        val details = adapter.getApprovedSpendings(EVENT_ID)

        assertThat(details).hasSize(1)
        assertThat(details.first().spendingId).isEqualTo(approved.id)
        assertThat(details.first().amount).isEqualTo(10_000L)
        assertThat(adapter.getApprovedSpendings(EMPTY_EVENT_ID)).isEmpty()
    }

    /** 보고서 표의 줄 순서가 매번 달라지면 같은 결산을 두 번 뽑았을 때 다르게 보인다. */
    @Test
    @DisplayName("목록은 지출일 오름차순으로, 같은 날이면 등록순으로 나온다")
    fun approvedListIsOrdered() {
        val second = save(spentOn = LocalDate.of(2026, 8, 20)).also { it.approve(OWNER_ID) }
        val third = save(spentOn = LocalDate.of(2026, 8, 20)).also { it.approve(OWNER_ID) }
        val first = save(spentOn = LocalDate.of(2026, 8, 18)).also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        assertThat(adapter.getApprovedSpendings(EVENT_ID).map { it.spendingId })
            .containsExactly(first.id, second.id, third.id)
    }

    @Test
    @DisplayName("태그는 저장 코드와 한글 라벨을 함께 준다")
    fun approvedListCarriesTagLabel() {
        save(tag = SpendingTag.ACCOMMODATION).also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        val detail = adapter.getApprovedSpendings(EVENT_ID).single()

        assertThat(detail.tag).isEqualTo("ACCOMMODATION")
        assertThat(detail.tagLabel).isEqualTo("숙박비")
    }

    @Test
    @DisplayName("기타 태그는 상세 내용까지 함께 준다")
    fun approvedListCarriesOtherDetail() {
        save(tag = SpendingTag.OTHER, otherDetail = "구급약 구입").also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        val detail = adapter.getApprovedSpendings(EVENT_ID).single()

        assertThat(detail.tagLabel).isEqualTo("기타")
        assertThat(detail.otherDetail).isEqualTo("구급약 구입")
    }

    /** 발급된 URL은 수 분 뒤 만료된다. 영구 보관되는 스냅샷에는 키가 남아야 한다(#92). */
    @Test
    @DisplayName("증빙은 URL이 아니라 저장소 키로 넘긴다")
    fun approvedListCarriesEvidenceKey() {
        val spending = save().also { it.approve(OWNER_ID) }
        spendingRepository.flush()

        val detail = adapter.getApprovedSpendings(EVENT_ID).single()

        assertThat(detail.evidenceStorageKey).isEqualTo("spendings/$EVENT_ID/$APPLICANT_ID/evidence.jpg")
        assertThat(detail.applicantUserId).isEqualTo(APPLICANT_ID)
        assertThat(detail.description).isEqualTo(spending.reason)
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
        spentOn: LocalDate = LocalDate.of(2026, 8, 20),
        tag: SpendingTag = SpendingTag.MEAL,
        otherDetail: String? = null,
    ): Spending = spendingRepository.save(
        Spending.create(
            eventId = eventId,
            applicantUserId = APPLICANT_ID,
            amount = amount,
            spentOn = spentOn,
            reason = "1일차 점심 식사",
            tag = tag,
            otherDetail = otherDetail,
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
