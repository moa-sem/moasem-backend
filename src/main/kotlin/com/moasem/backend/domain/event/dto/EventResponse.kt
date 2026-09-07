package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.Event
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
) {
    companion object {
        fun from(event: Event): EventListResponse = EventListResponse(
            eventId = event.id ?: error("저장되지 않은 행사는 응답으로 변환할 수 없습니다."),
            title = event.title,
            startAt = event.startAt,
            endAt = event.endAt,
            status = event.status,
            initialBudget = event.initialBudget,
        )
    }
}

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
) {
    companion object {
        fun from(
            event: Event,
            additionalBudget: Long,
            approvedSpending: Long,
        ): EventDetailResponse = EventDetailResponse(
            eventId = event.id ?: error("저장되지 않은 행사는 응답으로 변환할 수 없습니다."),
            groupId = event.groupId,
            title = event.title,
            description = event.description,
            startAt = event.startAt,
            endAt = event.endAt,
            status = event.status,
            initialBudget = event.initialBudget,
            additionalBudget = additionalBudget,
            totalBudget = event.calculateTotalBudget(additionalBudget),
            approvedSpending = approvedSpending,
            remainingBudget = event.calculateRemainingBudget(additionalBudget, approvedSpending),
        )
    }
}
