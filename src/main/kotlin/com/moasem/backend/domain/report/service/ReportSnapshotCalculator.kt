package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.ReportSnapshot
import com.moasem.backend.domain.report.service.port.EventSnapshotData
import org.springframework.stereotype.Component

/**
 * 행사 원자료로부터 결산 수치를 계산해 [ReportSnapshot]을 만든다.
 *
 * 여기서 나온 값이 보고서에 찍히는 모든 금액의 유일한 출처다. 확정된 스냅샷은 이후
 * 갱신하지 않으므로, 다시 계산할 일도 없고 다시 계산해서도 안 된다.
 */
@Component
class ReportSnapshotCalculator {

    fun calculate(data: EventSnapshotData): ReportSnapshot {
        check(data.status == CLOSED_STATUS) {
            "마감된 행사만 결산할 수 있습니다. 현재 상태: ${data.status}"
        }

        val totalBudget = data.initialBudget + data.budgetAdditions.sumOf { it.amount }
        val totalSpent = data.approvedSpendings.sumOf { it.amount }

        return ReportSnapshot(
            event = ReportSnapshot.EventSummary(
                eventId = data.eventId,
                title = data.title,
                startAt = data.startAt,
                endAt = data.endAt,
                groupId = data.groupId,
                groupName = data.groupName,
                participantCount = data.participantCount,
            ),
            budget = ReportSnapshot.BudgetSummary(
                initialBudget = data.initialBudget,
                additions = data.budgetAdditions
                    .sortedBy { it.addedAt }
                    .map {
                        ReportSnapshot.BudgetAdditionLine(
                            amount = it.amount,
                            reason = it.reason,
                            addedBy = it.addedBy,
                            addedAt = it.addedAt,
                        )
                    },
                totalBudget = totalBudget,
                totalSpent = totalSpent,
                // 예산을 초과하면 음수가 된다. 0으로 막지 않는다 — 초과 사실이 보고서에 드러나야 한다.
                remainingBalance = totalBudget - totalSpent,
            ),
            tagTotals = calculateTagTotals(data),
            spendings = data.approvedSpendings
                .sortedWith(compareBy({ it.spentAt }, { it.spendingId }))
                .map {
                    ReportSnapshot.SpendingLine(
                        spendingId = it.spendingId,
                        description = it.description,
                        amount = it.amount,
                        tag = it.tag,
                        payerName = it.payerName,
                        spentAt = it.spentAt,
                        receiptUrl = it.receiptUrl,
                    )
                },
        )
    }

    /**
     * 태그별 지출 합계와 건수.
     *
     * 금액이 큰 순으로 정렬해 보고서에서 비중이 큰 항목이 먼저 보이게 한다.
     * 금액이 같으면 태그 코드순으로 정렬해 순서가 매번 달라지지 않도록 한다.
     */
    private fun calculateTagTotals(data: EventSnapshotData): List<ReportSnapshot.TagTotal> =
        data.approvedSpendings
            .groupBy { it.tag }
            .map { (tag, spendings) ->
                ReportSnapshot.TagTotal(
                    tag = tag,
                    label = spendings.first().tagLabel,
                    amount = spendings.sumOf { it.amount },
                    count = spendings.size,
                )
            }
            .sortedWith(compareByDescending<ReportSnapshot.TagTotal> { it.amount }.thenBy { it.tag })

    companion object {
        private const val CLOSED_STATUS = "CLOSED"
    }
}
