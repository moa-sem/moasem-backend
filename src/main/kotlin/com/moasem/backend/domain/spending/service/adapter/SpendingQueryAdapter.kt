package com.moasem.backend.domain.spending.service.adapter

import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.repository.SpendingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * event 도메인이 요구하는 지출 집계 경계의 구현.
 *
 * 세 port가 모두 "이 행사의 지출을 한 줄로 요약해 달라"는 같은 요구라 한 클래스에 모았다.
 * 나누면 같은 레포지토리를 감싸기만 하는 파일이 셋으로 늘어난다.
 *
 * 조회 전용이며 event 도메인은 [com.moasem.backend.domain.spending.entity.Spending]을
 * 알지 못한다. 어떤 상태를 집계에 넣을지 판단하는 책임은 전부 여기에 있다.
 */
@Component
@Transactional(readOnly = true)
class SpendingQueryAdapter(
    private val spendingRepository: SpendingRepository,
) : ApprovedSpendingTotalProvider, PendingSpendingCountProvider, SpendingHistoryProvider {

    /**
     * 승인된 지출만 더한다.
     *
     * PENDING은 아직 모임장이 인정하지 않은 금액이고 REJECTED는 인정하지 않기로 한 금액이다.
     * 둘 다 예산에서 빠져서는 안 된다.
     */
    override fun getApprovedSpendingTotal(eventId: Long): Long =
        spendingRepository.sumAmountByEventIdAndStatus(eventId, SpendingStatus.APPROVED)

    /** 마감 전에 처리되지 않은 신청이 남아 있는지 확인하는 용도다. */
    override fun getPendingSpendingCount(eventId: Long): Long =
        spendingRepository.countByEventIdAndStatus(eventId, SpendingStatus.PENDING)

    /** 상태를 가리지 않는다. 반려된 신청도 "지출을 시도한 이력"으로 본다. */
    override fun hasAnySpending(eventId: Long): Boolean =
        spendingRepository.existsByEventId(eventId)
}
