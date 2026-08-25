package com.moasem.backend.domain.report.service.port

import java.time.LocalDateTime

/**
 * 테스트용 [EventSnapshotProvider].
 *
 * event·spending 도메인 없이 결산 계산을 검증하기 위해 원하는 원자료를 직접 주입한다.
 */
class FakeEventSnapshotProvider : EventSnapshotProvider {

    private val store = mutableMapOf<Long, EventSnapshotData>()

    fun given(data: EventSnapshotData) {
        store[data.eventId] = data
    }

    override fun fetch(eventId: Long): EventSnapshotData =
        store[eventId] ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")

    companion object {
        /** 필요한 필드만 덮어써서 쓰는 기본 원자료. */
        fun sampleData(
            eventId: Long = 1L,
            status: String = "CLOSED",
            participantCount: Int? = 8,
            initialBudget: Long = 500_000L,
            budgetAdditions: List<BudgetAdditionData> = emptyList(),
            approvedSpendings: List<ApprovedSpendingData> = listOf(sampleSpending()),
        ): EventSnapshotData {
            val startAt = LocalDateTime.of(2026, 8, 24, 10, 0)
            return EventSnapshotData(
                eventId = eventId,
                title = "여름 MT",
                startAt = startAt,
                endAt = startAt.plusDays(2),
                status = status,
                groupId = 10L,
                groupName = "백엔드 스터디",
                participantCount = participantCount,
                initialBudget = initialBudget,
                budgetAdditions = budgetAdditions,
                approvedSpendings = approvedSpendings,
            )
        }

        fun sampleSpending(
            spendingId: Long = 100L,
            description: String = "저녁 식사",
            amount: Long = 320_000L,
            tag: String = "MEAL",
            payerName: String = "김석주",
            receiptUrl: String? = null,
        ): ApprovedSpendingData = ApprovedSpendingData(
            spendingId = spendingId,
            description = description,
            amount = amount,
            tag = tag,
            payerName = payerName,
            spentAt = LocalDateTime.of(2026, 8, 25, 19, 0),
            receiptUrl = receiptUrl,
        )
    }
}
