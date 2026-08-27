package com.moasem.backend.domain.event.converter

import com.moasem.backend.domain.event.dto.EventDetailResponse
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.Event

object EventConverter {

    fun toListResponse(event: Event): EventListResponse = EventListResponse(
        eventId = event.id ?: error("저장되지 않은 행사는 응답으로 변환할 수 없습니다."),
        title = event.title,
        startAt = event.startAt,
        endAt = event.endAt,
        status = event.status,
        initialBudget = event.initialBudget,
    )

    fun toDetailResponse(event: Event, additionalBudget: Long): EventDetailResponse = EventDetailResponse(
        eventId = event.id ?: error("저장되지 않은 행사는 응답으로 변환할 수 없습니다."),
        groupId = event.groupId,
        title = event.title,
        description = event.description,
        startAt = event.startAt,
        endAt = event.endAt,
        status = event.status,
        initialBudget = event.initialBudget,
        additionalBudget = additionalBudget,
        totalBudget = event.initialBudget + additionalBudget,
    )
}
