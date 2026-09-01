package com.moasem.backend.domain.spending.dto

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "지출 상세")
data class SpendingDetailResponse(
    val spendingId: Long,
    val eventId: Long,
    val applicantUserId: Long,
    val amount: Long,
    val spentOn: LocalDate,
    val reason: String,
    val tag: SpendingTag,
    @field:Schema(description = "태그 한글 라벨", example = "식비")
    val tagLabel: String,
    val otherDetail: String?,
    val evidenceType: EvidenceType,
    val status: SpendingStatus,
    val processedByUserId: Long?,
    val rejectionReason: String?,
    val processedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

@Schema(description = "지출 목록 항목")
data class SpendingListResponse(
    val spendingId: Long,
    val applicantUserId: Long,
    val amount: Long,
    val spentOn: LocalDate,
    val reason: String,
    val tag: SpendingTag,
    @field:Schema(description = "태그 한글 라벨", example = "식비")
    val tagLabel: String,
    val status: SpendingStatus,
    val createdAt: LocalDateTime?,
)

@Schema(description = "증빙 이미지 조회 URL")
data class EvidenceDownloadUrlResponse(
    @field:Schema(description = "발급된 조회 URL. 만료 전까지만 유효하다.")
    val downloadUrl: String,
    @field:Schema(description = "URL 만료 시각")
    val expiresAt: LocalDateTime,
)
