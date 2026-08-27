package com.moasem.backend.domain.event.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

@Schema(description = "추가 예산 등록 요청")
data class CreateBudgetAdditionRequest(
    @field:Positive(message = "추가 예산 금액은 0원보다 커야 합니다.")
    @field:Schema(description = "추가 예산 금액(원)", example = "100000")
    val amount: Long,
    @field:NotBlank(message = "추가 예산 사유는 비어 있을 수 없습니다.")
    @field:Schema(description = "추가 예산 사유", example = "참가 인원 증가")
    val reason: String,
)
