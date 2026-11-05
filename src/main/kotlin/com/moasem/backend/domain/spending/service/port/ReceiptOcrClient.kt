package com.moasem.backend.domain.spending.service.port

import com.moasem.backend.domain.spending.entity.SpendingTag
import java.time.LocalDate

/**
 * 영수증 이미지에서 지출 신청 폼에 채울 값을 뽑는다.
 *
 * 결과는 입력 보조용 초안이다. 사용자가 확인·수정한 뒤 일반 지출 신청으로 저장하므로
 * 여기서 나온 값은 어디에도 저장하지 않는다.
 */
interface ReceiptOcrClient {

    /**
     * 읽지 못한 값은 null로 채운다. 호출 자체가 실패하면 예외를 던지고,
     * 호출부가 빈 결과로 바꿔 내보낸다.
     */
    fun recognize(image: ByteArray, mimeType: String): ReceiptOcrResult
}

data class ReceiptOcrResult(
    val amount: Long?,
    val spentOn: LocalDate?,
    val merchantName: String?,
    val tag: SpendingTag?,
) {
    companion object {
        val EMPTY = ReceiptOcrResult(amount = null, spentOn = null, merchantName = null, tag = null)
    }
}
