package com.moasem.backend.domain.spending.dto

import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.SpendingTag
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "지출 신청 요청")
data class CreateSpendingRequest(
    @field:Positive(message = "지출 금액은 0원보다 커야 합니다.")
    @field:Schema(description = "지출 금액(원)", example = "15000")
    val amount: Long,
    @field:NotNull(message = "지출일은 필수입니다.")
    @field:Schema(description = "실제 지출일", example = "2026-08-20")
    val spentOn: LocalDate?,
    @field:NotBlank(message = "지출 사유는 비어 있을 수 없습니다.")
    @field:Size(max = REASON_MAX_LENGTH, message = "지출 사유는 500자 이하여야 합니다.")
    @field:Schema(description = "지출 사유", example = "1일차 점심 식사")
    val reason: String,
    @field:NotNull(message = "지출 태그는 필수입니다.")
    @field:Schema(description = "지출 태그", example = "MEAL")
    val tag: SpendingTag?,
    @field:Size(max = OTHER_DETAIL_MAX_LENGTH, message = "기타 상세는 200자 이하여야 합니다.")
    @field:Schema(description = "기타 상세 (tag가 OTHER일 때 필수)", example = "축하 화환 구입")
    val otherDetail: String? = null,
    @field:NotNull(message = "증빙 정보는 필수입니다.")
    @field:Valid
    @field:Schema(description = "증빙 정보")
    val evidence: EvidenceRequest?,
) {
    @AssertTrue(message = "기타 태그를 선택하면 상세 내용을 입력해야 합니다.")
    fun isOtherDetailProvided(): Boolean = tag != SpendingTag.OTHER || !otherDetail.isNullOrBlank()

    companion object {
        const val REASON_MAX_LENGTH = 500
        const val OTHER_DETAIL_MAX_LENGTH = 200
    }
}

@Schema(description = "증빙 정보. 업로드 URL 발급 응답으로 받은 저장 키를 그대로 실어 보낸다.")
data class EvidenceRequest(
    @field:NotNull(message = "증빙 종류는 필수입니다.")
    @field:Schema(description = "증빙 종류", example = "RECEIPT")
    val type: EvidenceType?,
    @field:NotBlank(message = "증빙 저장 키는 비어 있을 수 없습니다.")
    @field:Schema(description = "업로드 URL 발급 시 받은 저장 키")
    val storageKey: String,
    @field:NotBlank(message = "증빙 파일 형식은 필수입니다.")
    @field:Schema(description = "증빙 파일 MIME 타입", example = "image/jpeg")
    val mimeType: String,
    @field:PositiveOrZero(message = "증빙 파일 크기는 0 이상이어야 합니다.")
    @field:Schema(description = "증빙 파일 크기(byte)", example = "204800")
    val fileSize: Long? = null,
)
