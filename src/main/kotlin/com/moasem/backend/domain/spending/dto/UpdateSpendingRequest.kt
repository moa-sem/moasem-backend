package com.moasem.backend.domain.spending.dto

import com.moasem.backend.domain.spending.entity.SpendingTag
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 지출 수정 요청.
 *
 * 일부 필드만 보내는 방식이 아니라 신청 내용 전체를 다시 보낸다. 증빙까지 통째로 갈아끼우기
 * 때문이다. 증빙을 바꾸지 않는다면 기존 저장 키를 그대로 다시 실어 보내면 된다.
 */
@Schema(description = "지출 수정 요청")
data class UpdateSpendingRequest(
    @field:Positive(message = "지출 금액은 0원보다 커야 합니다.")
    @field:Schema(description = "지출 금액(원)", example = "15000")
    val amount: Long,
    @field:NotNull(message = "지출일은 필수입니다.")
    @field:Schema(description = "실제 지출일", example = "2026-08-20")
    val spentOn: LocalDate?,
    @field:NotBlank(message = "지출 사유는 비어 있을 수 없습니다.")
    @field:Size(max = CreateSpendingRequest.REASON_MAX_LENGTH, message = "지출 사유는 500자 이하여야 합니다.")
    @field:Schema(description = "지출 사유", example = "1일차 저녁 식사")
    val reason: String,
    @field:NotNull(message = "지출 태그는 필수입니다.")
    @field:Schema(description = "지출 태그", example = "MEAL")
    val tag: SpendingTag?,
    @field:Size(max = CreateSpendingRequest.OTHER_DETAIL_MAX_LENGTH, message = "기타 상세는 200자 이하여야 합니다.")
    @field:Schema(description = "기타 상세 (tag가 OTHER일 때 필수)", example = "축하 화환 구입")
    val otherDetail: String? = null,
    @field:NotNull(message = "증빙 정보는 필수입니다.")
    @field:Valid
    @field:Schema(description = "증빙 정보. 바꾸지 않으려면 기존 저장 키를 그대로 보낸다.")
    val evidence: EvidenceRequest?,
) {
    @AssertTrue(message = "기타 태그를 선택하면 상세 내용을 입력해야 합니다.")
    fun isOtherDetailProvided(): Boolean = tag != SpendingTag.OTHER || !otherDetail.isNullOrBlank()
}
