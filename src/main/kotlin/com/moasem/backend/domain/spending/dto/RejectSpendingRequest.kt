package com.moasem.backend.domain.spending.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "지출 반려 요청")
data class RejectSpendingRequest(
    @field:NotBlank(message = "반려 사유는 비어 있을 수 없습니다.")
    @field:Size(max = REJECTION_REASON_MAX_LENGTH, message = "반려 사유는 500자 이하여야 합니다.")
    @field:Schema(description = "반려 사유. 신청자가 무엇을 고쳐야 하는지 알 수 있어야 한다.", example = "증빙 이미지가 흐려 금액을 확인할 수 없습니다.")
    val reason: String,
) {
    companion object {
        const val REJECTION_REASON_MAX_LENGTH = 500
    }
}
