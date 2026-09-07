package com.moasem.backend.domain.spending.dto

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
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
) {
    companion object {
        /**
         * 응답에 [Spending.evidenceStorageKey]를 담지 않는다.
         *
         * 저장 키가 노출되면 증빙 조회 API의 권한 검증을 우회할 실마리가 된다.
         * 증빙 이미지는 별도 URL 발급 API로만 접근한다. 빠진 필드가 아니라 뺀 필드다.
         */
        fun from(spending: Spending): SpendingDetailResponse = SpendingDetailResponse(
            spendingId = spending.requireId(),
            eventId = spending.eventId,
            applicantUserId = spending.applicantUserId,
            amount = spending.amount,
            spentOn = spending.spentOn,
            reason = spending.reason,
            tag = spending.tag,
            tagLabel = spending.tag.label,
            otherDetail = spending.otherDetail,
            evidenceType = spending.evidenceType,
            status = spending.status,
            processedByUserId = spending.processedByUserId,
            rejectionReason = spending.rejectionReason,
            processedAt = spending.processedAt,
            createdAt = spending.createdAt,
        )
    }
}

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
) {
    companion object {
        fun from(spending: Spending): SpendingListResponse = SpendingListResponse(
            spendingId = spending.requireId(),
            applicantUserId = spending.applicantUserId,
            amount = spending.amount,
            spentOn = spending.spentOn,
            reason = spending.reason,
            tag = spending.tag,
            tagLabel = spending.tag.label,
            status = spending.status,
            createdAt = spending.createdAt,
        )
    }
}

/** 저장 전 엔티티는 식별자가 없어 응답으로 만들 수 없다. */
private fun Spending.requireId(): Long =
    id ?: error("저장되지 않은 지출은 응답으로 변환할 수 없습니다.")

@Schema(description = "증빙 이미지 조회 URL")
data class EvidenceDownloadUrlResponse(
    @field:Schema(description = "발급된 조회 URL. 만료 전까지만 유효하다.")
    val downloadUrl: String,
    @field:Schema(description = "URL 만료 시각")
    val expiresAt: LocalDateTime,
)
