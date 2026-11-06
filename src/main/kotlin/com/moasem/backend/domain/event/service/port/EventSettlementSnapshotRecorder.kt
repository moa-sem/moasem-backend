package com.moasem.backend.domain.event.service.port

import java.time.LocalDateTime

/**
 * 행사 마감 트랜잭션에서 확정한 결산 값을 보고서 스냅샷으로 저장하는 경계다.
 */
interface EventSettlementSnapshotRecorder {

    fun record(eventId: Long, settlement: EventSettlementSnapshot)
}

data class EventSettlementSnapshot(
    val totalBudget: Long,
    val approvedSpending: Long,
    val remainingBudget: Long,
    val participantCount: Int,
    val refundPerPerson: Long,
    val refundRemainder: Long,
    val additionalChargePerPerson: Long,
    val overCollectedAmount: Long,
    val closedAt: LocalDateTime,
)
