package com.moasem.backend.domain.report.converter

import com.moasem.backend.domain.report.dto.BudgetAdditionResponse
import com.moasem.backend.domain.report.dto.BudgetSummaryResponse
import com.moasem.backend.domain.report.dto.EventSummaryResponse
import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.domain.report.dto.SpendingLineResponse
import com.moasem.backend.domain.report.dto.TagTotalResponse
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportSnapshot
import org.springframework.stereotype.Component

/**
 * Report 엔티티를 응답 DTO로 변환한다.
 *
 * 모든 수치는 확정된 스냅샷에서만 가져온다. 원본 데이터를 다시 조회하지 않으므로
 * 몇 번을 조회해도 같은 값이 나온다.
 */
@Component
class ReportConverter {

    fun toStatusResponse(report: Report) = ReportStatusResponse(
        eventId = report.eventId,
        status = report.status,
        aiStatus = report.aiStatus,
        downloadable = report.isDownloadable,
        retryable = report.status.isRetryable,
        failureReason = report.failureReason,
        generatedAt = report.generatedAt,
    )

    fun toDetailResponse(report: Report, snapshot: ReportSnapshot): ReportDetailResponse {
        // 지출 내역에도 한글 라벨을 붙인다. 라벨은 tagTotals가 이미 갖고 있다.
        val labels = snapshot.tagTotals.associate { it.tag to it.label }

        return ReportDetailResponse(
            eventId = report.eventId,
            status = report.status,
            aiStatus = report.aiStatus,
            aiSummary = report.aiSummary,
            event = EventSummaryResponse(
                title = snapshot.event.title,
                startAt = snapshot.event.startAt,
                endAt = snapshot.event.endAt,
                groupName = snapshot.event.groupName,
                participantCount = snapshot.event.participantCount,
            ),
            budget = BudgetSummaryResponse(
                initialBudget = snapshot.budget.initialBudget,
                totalBudget = snapshot.budget.totalBudget,
                totalSpent = snapshot.budget.totalSpent,
                remainingBalance = snapshot.budget.remainingBalance,
                additions = snapshot.budget.additions.map {
                    BudgetAdditionResponse(
                        amount = it.amount,
                        reason = it.reason,
                        addedBy = it.addedBy,
                        addedAt = it.addedAt,
                    )
                },
            ),
            tagTotals = snapshot.tagTotals.map {
                TagTotalResponse(tag = it.tag, label = it.label, amount = it.amount, count = it.count)
            },
            spendings = snapshot.spendings.map {
                SpendingLineResponse(
                    spendingId = it.spendingId,
                    description = it.description,
                    amount = it.amount,
                    tag = it.tag,
                    label = labels[it.tag] ?: it.tag,
                    payerName = it.payerName,
                    spentAt = it.spentAt,
                    receiptUrl = it.receiptUrl,
                )
            },
            generatedAt = report.generatedAt,
        )
    }
}
