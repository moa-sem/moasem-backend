package com.moasem.backend.domain.event.service.port

/**
 * 행사별 PENDING 지출 신청 개수를 제공하는 경계다.
 *
 * PENDING 상태를 판별하는 책임은 실제 spending 어댑터에 있으며,
 * PENDING 지출 신청이 없으면 [getPendingSpendingCount]는 0L을 반환한다.
 */
interface PendingSpendingCountProvider {

    fun getPendingSpendingCount(eventId: Long): Long
}
