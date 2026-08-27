package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.EventStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "행사 목록 항목")
data class EventListResponse(
    val eventId: Long,
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: EventStatus,
    val initialBudget: Long,
)

@Schema(description = "행사 상세")
data class EventDetailResponse(
    val eventId: Long,
    val groupId: Long,
    val title: String,
    val description: String?,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: EventStatus,
    val initialBudget: Long,
    val additionalBudget: Long,
    val totalBudget: Long,
    val approvedSpending: Long,
    val remainingBudget: Long,
)
