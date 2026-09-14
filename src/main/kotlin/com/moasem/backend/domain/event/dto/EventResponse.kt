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
    @field:Schema(description = "최초 예산과 추가 예산을 합한 총예산(원)", example = "600000")
    val totalBudget: Long,
    @field:Schema(description = "총예산에서 승인 지출을 뺀 잔여 예산(원)", example = "350000")
    val remainingBudget: Long,
    @field:Schema(description = "마감 시 저장된 참여 인원. 진행 중인 행사는 null", example = "12", nullable = true)
    val participantCount: Int?,
) {
    companion object {
        fun from(
            event: Event,
            additionalBudget: Long,
            approvedSpending: Long,
        ): EventListResponse = EventListResponse(
            eventId = event.id ?: error("저장되지 않은 행사는 응답으로 변환할 수 없습니다."),
            title = event.title,
            startAt = event.startAt,
            endAt = event.endAt,
            status = event.status,
            initialBudget = event.initialBudget,
            totalBudget = event.calculateTotalBudget(additionalBudget),
            remainingBudget = event.calculateRemainingBudget(additionalBudget, approvedSpending),
            participantCount = if (event.status == EventStatus.CLOSED) event.participantCount else null,
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
