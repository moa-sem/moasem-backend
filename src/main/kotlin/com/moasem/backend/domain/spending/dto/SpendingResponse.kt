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
