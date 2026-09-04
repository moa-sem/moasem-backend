package com.moasem.backend.domain.event.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min

@Schema(description = "행사 마감 확정 요청")
data class CloseEventRequest(
    @field:Min(value = 1, message = "행사 참여 인원은 1명 이상이어야 합니다.")
    @field:Schema(description = "확정 행사 참여 인원", example = "3", minimum = "1")
    val participantCount: Int,
)
