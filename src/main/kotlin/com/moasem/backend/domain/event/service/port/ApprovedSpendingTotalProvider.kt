package com.moasem.backend.domain.event.service.port

/**
 * 행사별 승인 지출 금액 합계를 제공하는 경계다.
 *
 * PENDING·REJECTED 지출을 제외하는 책임은 실제 spending 어댑터에 있으며,
 * 승인 지출이 없으면 [getApprovedSpendingTotal]은 0L을 반환한다.
 * 목록 조회용 [getApprovedSpendingTotals]도 요청한 각 행사 ID에 대해 0L을 포함한다.
 */
interface ApprovedSpendingTotalProvider {

    fun getApprovedSpendingTotal(eventId: Long): Long

    fun getApprovedSpendingTotals(eventIds: Collection<Long>): Map<Long, Long>
}
