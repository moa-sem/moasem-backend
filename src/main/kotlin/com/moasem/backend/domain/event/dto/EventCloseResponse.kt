package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.EventStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "행사 마감 결과")
data class EventCloseResponse(
    val eventId: Long,
    val status: EventStatus,
    val participantCount: Int,
    val closedAt: LocalDateTime,
)
