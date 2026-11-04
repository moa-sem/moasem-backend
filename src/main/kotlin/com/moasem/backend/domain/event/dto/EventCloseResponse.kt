package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.EventStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "행사 마감 결과")
data class EventCloseResponse(
    val eventId: Long,
    val status: EventStatus,
    @field:Schema(description = "분배 인원", example = "3")
    val participantCount: Int,
    val closedAt: LocalDateTime,
    @field:Schema(description = "양수 잔액을 분배 인원으로 나눈 1인당 반환액(원 단위 내림)", example = "116666")
    val refundPerPerson: Long,
    @field:Schema(description = "1인당 반환 후 남는 금액", example = "2")
    val refundRemainder: Long,
    @field:Schema(description = "부족액을 분배 인원으로 나눈 1인당 추가 부담액(원 단위 올림)", example = "0")
    val additionalChargePerPerson: Long,
    @field:Schema(description = "올림된 추가 부담액으로 부족액을 초과해 걷게 되는 금액", example = "0")
    val overCollectedAmount: Long,
)
