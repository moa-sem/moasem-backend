package com.moasem.backend.domain.event.service.port

/**
 * 행사에 상태와 관계없이 지출 신청 이력이 존재하는지 확인하는 경계다.
 * 실제 구현은 spending 도메인이 준비된 뒤 별도 어댑터로 제공한다.
 */
interface SpendingHistoryProvider {

    fun hasAnySpending(eventId: Long): Boolean
}
