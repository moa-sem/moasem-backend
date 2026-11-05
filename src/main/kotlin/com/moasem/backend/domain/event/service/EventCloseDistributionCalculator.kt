package com.moasem.backend.domain.event.service

import org.springframework.stereotype.Component

@Component
class EventCloseDistributionCalculator {

    fun calculate(
        totalBudget: Long,
        approvedSpending: Long,
        participantCount: Int,
    ): EventCloseDistribution {
        require(participantCount > 0) { "분배 인원은 1명 이상이어야 합니다." }

        val remainingBudget = totalBudget - approvedSpending
        val participantCountAsLong = participantCount.toLong()

        if (remainingBudget > 0L) {
            return EventCloseDistribution(
                remainingBudget = remainingBudget,
                refundPerPerson = remainingBudget / participantCountAsLong,
                refundRemainder = remainingBudget % participantCountAsLong,
                additionalChargePerPerson = 0L,
                overCollectedAmount = 0L,
            )
        }

        if (remainingBudget < 0L) {
            val shortage = -remainingBudget
            val quotient = shortage / participantCountAsLong
            val remainder = shortage % participantCountAsLong
            val additionalChargePerPerson = quotient + if (remainder == 0L) 0L else 1L

            return EventCloseDistribution(
                remainingBudget = remainingBudget,
                refundPerPerson = 0L,
                refundRemainder = 0L,
                additionalChargePerPerson = additionalChargePerPerson,
                overCollectedAmount = additionalChargePerPerson * participantCountAsLong - shortage,
            )
        }

        return EventCloseDistribution(
            remainingBudget = 0L,
            refundPerPerson = 0L,
            refundRemainder = 0L,
            additionalChargePerPerson = 0L,
            overCollectedAmount = 0L,
        )
    }
}

data class EventCloseDistribution(
    val remainingBudget: Long,
    val refundPerPerson: Long,
    val refundRemainder: Long,
    val additionalChargePerPerson: Long,
    val overCollectedAmount: Long,
)
