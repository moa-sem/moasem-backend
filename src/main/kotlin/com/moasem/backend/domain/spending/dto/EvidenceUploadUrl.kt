package com.moasem.backend.domain.spending.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

@Schema(description = "증빙 업로드 URL 발급 요청")
data class EvidenceUploadUrlRequest(
    @field:NotBlank(message = "증빙 파일 형식은 필수입니다.")
    @field:Schema(description = "올릴 파일의 MIME 타입 (image/jpeg 또는 image/png)", example = "image/jpeg")
    val mimeType: String,
    @field:Positive(message = "증빙 파일 크기는 0보다 커야 합니다.")
    @field:Schema(description = "올릴 파일의 크기(byte)", example = "204800")
    val fileSize: Long,
)

@Schema(description = "증빙 업로드 URL 발급 응답")
data class EvidenceUploadUrlResponse(
    @field:Schema(description = "발급된 업로드 URL. 이 URL로 PUT 요청해 파일을 올린다.")
    val uploadUrl: String,
    @field:Schema(description = "저장 키. 업로드 후 지출 신청 요청에 그대로 실어 보낸다.")
    val storageKey: String,
    @field:Schema(description = "URL 만료 시각")
    val expiresAt: LocalDateTime,
)
