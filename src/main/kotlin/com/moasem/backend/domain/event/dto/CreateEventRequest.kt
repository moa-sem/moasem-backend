package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.Event
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Schema(description = "행사 생성 요청")
data class CreateEventRequest(
    @field:NotBlank(message = "행사 제목은 비어 있을 수 없습니다.")
    @field:Size(max = Event.TITLE_MAX_LENGTH, message = "행사 제목은 100자 이하여야 합니다.")
    @field:Schema(description = "행사 제목", example = "여름 MT")
    val title: String,
    @field:Schema(description = "행사 설명", example = "2박 3일 여름 MT입니다.")
    val description: String? = null,
    @field:NotNull(message = "행사 시작 시각은 필수입니다.")
    @field:Schema(description = "행사 시작 시각", example = "2026-08-28T10:00:00")
    val startAt: LocalDateTime?,
    @field:NotNull(message = "행사 종료 시각은 필수입니다.")
    @field:Schema(description = "행사 종료 시각", example = "2026-08-30T12:00:00")
    val endAt: LocalDateTime?,
    @field:PositiveOrZero(message = "최초 예산은 0원 이상이어야 합니다.")
    @field:Schema(description = "최초 예산(원)", example = "500000")
    val initialBudget: Long,
) {
    @AssertTrue(message = "행사 종료 시각은 시작 시각보다 늦어야 합니다.")
    fun isValidPeriod(): Boolean = startAt == null || endAt == null || startAt.isBefore(endAt)
}
