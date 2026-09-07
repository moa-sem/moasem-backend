package com.moasem.backend.domain.report.entity

import java.time.LocalDateTime

/**
 * 보고서 생성 시점에 확정된 결산 결과.
 *
 * 한 번 저장하면 절대 갱신하지 않는다. 다운로드할 때마다 원본 지출 데이터를 다시 집계하면
 * 이후 데이터 변경에 따라 결과가 달라질 수 있어, 확정 시점의 수치를 통째로 얼려 둔다.
 * PDF·CSV에 찍히는 모든 금액은 오직 이 스냅샷에서만 가져온다.
 */
data class ReportSnapshot(
    val event: EventSummary,
    val budget: BudgetSummary,
    val tagTotals: List<TagTotal>,
    val spendings: List<SpendingLine>,
) {
    data class EventSummary(
        val eventId: Long,
        val title: String,
        val startAt: LocalDateTime,
        val endAt: LocalDateTime,
        val groupId: Long,
        val groupName: String,
        /** 행사 참여 인원. event 도메인에서 마감 시 확정되며, 아직 제공되지 않을 수 있다. */
        val participantCount: Int? = null,
    )

    data class BudgetSummary(
        val initialBudget: Long,
        val additions: List<BudgetAdditionLine>,
        val totalBudget: Long,
        val totalSpent: Long,
        val remainingBalance: Long,
    )

    data class BudgetAdditionLine(
        val amount: Long,
        val reason: String,
        /** 예산을 추가한 사람. 과거 스냅샷에는 없을 수 있어 기본값을 둔다. */
        val addedBy: String? = null,
        val addedAt: LocalDateTime,
    )

    data class TagTotal(
        val tag: String,
        val label: String,
        val amount: Long,
        val count: Int,
    )

    data class SpendingLine(
        val spendingId: Long,
        val description: String,
        val amount: Long,
        val tag: String,
        val payerName: String,
        val spentAt: LocalDateTime,
        /**
         * 결산 시점에 붙어 있던 증빙의 저장소 키. 응답으로 내보내지 않는다.
         *
         * 저장소 구조는 내부 사정이라 밖으로 드러낼 이유가 없다. 그래도 스냅샷에는 남긴다.
         * 나중에 원본 지출이 바뀌어도 "결산할 때 이 증빙이 있었다"는 사실은 바뀌지 않아야 한다.
         */
        val receiptKey: String?,
    )
}
