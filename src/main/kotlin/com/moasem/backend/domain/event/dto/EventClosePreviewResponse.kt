package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.EventStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "행사 마감 미리보기")
data class EventClosePreviewResponse(
    val eventId: Long,
    val title: String,
    val status: EventStatus,
    val participantCount: Int,
    val pendingSpendingCount: Long,
    val initialBudget: Long,
    val additionalBudget: Long,
    val totalBudget: Long,
    val approvedSpending: Long,
    val remainingBudget: Long,
)
