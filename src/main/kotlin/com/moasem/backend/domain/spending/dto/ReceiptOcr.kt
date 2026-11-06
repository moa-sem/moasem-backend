package com.moasem.backend.domain.spending.dto

import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.service.port.ReceiptOcrResult
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(description = "영수증 인식 요청")
data class ReceiptOcrRequest(
    @field:NotBlank(message = "증빙 저장 키는 비어 있을 수 없습니다.")
    @field:Schema(description = "업로드 URL 발급 시 받은 저장 키. 업로드가 끝난 뒤 호출한다.")
    val storageKey: String,
)

@Schema(description = "영수증 인식 결과. 신청 폼을 미리 채우는 용도이며, 읽지 못한 값은 null이다.")
data class ReceiptOcrResponse(
    @field:Schema(description = "결제 총액(원)", example = "15000", nullable = true)
    val amount: Long?,
    @field:Schema(description = "결제일", example = "2026-08-20", nullable = true)
    val spentOn: LocalDate?,
    @field:Schema(description = "상호명. 지출 사유 기본값으로 쓸 수 있다.", example = "김밥천국 강남점", nullable = true)
    val merchantName: String?,
    @field:Schema(description = "추천 지출 태그", example = "MEAL", nullable = true)
    val tag: SpendingTag?,
) {
    companion object {
        fun from(result: ReceiptOcrResult) = ReceiptOcrResponse(
            amount = result.amount,
            spentOn = result.spentOn,
            merchantName = result.merchantName,
            tag = result.tag,
        )
    }
}
