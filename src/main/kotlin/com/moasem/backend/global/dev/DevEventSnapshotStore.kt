package com.moasem.backend.global.dev

import com.moasem.backend.domain.report.service.port.ApprovedSpendingData
import com.moasem.backend.domain.report.service.port.BudgetAdditionData
import com.moasem.backend.domain.report.service.port.EventSnapshotData
import com.moasem.backend.domain.report.service.port.EventSnapshotProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 로컬에서 결산 원자료를 직접 넣어 볼 수 있게 하는 [EventSnapshotProvider].
 *
 * spending·group 도메인이 아직 없어 실제 어댑터를 만들 수 없다. 그렇다고 아무 행사에나
 * 그럴듯한 가짜 금액을 돌려주면 잘못된 값이 스냅샷에 확정 저장되고, 스냅샷은 불변이라
 * 되돌릴 수 없다.
 *
 * 그래서 [seed]로 명시적으로 등록한 행사에만 응답한다. 등록하지 않은 행사는 그대로 실패한다.
 * 개발자가 "이건 샘플이다"라고 직접 표시한 경우에만 값이 생긴다.
 */
@Profile("local")
@Component
class DevEventSnapshotStore : EventSnapshotProvider {

    private val store = ConcurrentHashMap<Long, EventSnapshotData>()

    override fun fetch(eventId: Long): EventSnapshotData =
        store[eventId] ?: throw UnsupportedOperationException(
            "EventSnapshotProvider 어댑터가 아직 없습니다. " +
                "개발용 샘플로 확인하려면 POST /api/v1/dev/reports/$eventId 를 먼저 호출하세요.",
        )

    /** 해당 행사에 샘플 원자료를 등록하고 그 내용을 돌려준다. */
    fun seed(eventId: Long): EventSnapshotData = sample(eventId).also { store[eventId] = it }

    /**
     * 보고서 모양을 확인하기 위한 샘플.
     *
     * 태그별 집계와 예산 추가가 표에 어떻게 나오는지 보려면 항목이 여럿 필요해서
     * 지출을 태그별로 흩어 두고 예산 추가도 넣었다.
     */
    private fun sample(eventId: Long) = EventSnapshotData(
        eventId = eventId,
        title = "여름 MT",
        startAt = BASE_TIME,
        endAt = BASE_TIME.plusDays(2),
        status = "CLOSED",
        groupId = SAMPLE_GROUP_ID,
        groupName = "백엔드 스터디",
        participantCount = 12,
        initialBudget = 800_000L,
        budgetAdditions = listOf(
            BudgetAdditionData(
                amount = 150_000L,
                reason = "숙소 인원 추가",
                addedBy = "김소담",
                addedAt = BASE_TIME.plusHours(3),
            ),
        ),
        approvedSpendings = listOf(
            spending(1L, "펜션 2박", 420_000L, "LODGING", "숙박비", "윤석주", BASE_TIME),
            spending(2L, "첫날 저녁 고기", 186_000L, "MEAL", "식비", "김소담", BASE_TIME.plusHours(9)),
            spending(3L, "장보기", 74_500L, "MEAL", "식비", "이도현", BASE_TIME.plusHours(6)),
            spending(4L, "전세버스 왕복", 210_000L, "TRANSPORT", "교통비", "윤석주", BASE_TIME.minusHours(2)),
            spending(5L, "보드게임 대여", 35_000L, "ACTIVITY", "활동비", "박서진", BASE_TIME.plusDays(1)),
        ),
    )

    private fun spending(
        id: Long,
        description: String,
        amount: Long,
        tag: String,
        tagLabel: String,
        payerName: String,
        spentAt: LocalDateTime,
    ) = ApprovedSpendingData(
        spendingId = id,
        description = description,
        amount = amount,
        tag = tag,
        tagLabel = tagLabel,
        payerName = payerName,
        spentAt = spentAt,
        receiptUrl = null,
    )

    companion object {
        /** 로컬 스텁 [com.moasem.backend.global.stub.LocalPortStubs]가 모든 사용자를 구성원으로 보므로 값은 아무거나 무방하다. */
        const val SAMPLE_GROUP_ID = 10L

        private val BASE_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 24, 10, 0)
    }
}
