package com.moasem.backend.domain.spending.service.adapter

import com.moasem.backend.domain.report.service.adapter.ai.GeminiProperties
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.service.port.ReceiptOcrResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

/** 모델 응답 파싱. 실제 Gemini는 호출하지 않는다. */
class GeminiReceiptOcrClientTest {

    private val client = GeminiReceiptOcrClient(GeminiProperties(apiKey = "test"), jacksonObjectMapper())

    @Test
    fun `정상 응답을 그대로 옮긴다`() {
        val result = client.parse(
            """{"amount":15000,"spentOn":"2026-08-20","merchantName":" 김밥천국 ","tag":"MEAL"}""",
        )

        assertThat(result).isEqualTo(ReceiptOcrResult(15_000L, LocalDate.of(2026, 8, 20), "김밥천국", SpendingTag.MEAL))
    }

    @Test
    fun `형식이 어긋난 필드만 비우고 나머지는 살린다`() {
        val result = client.parse(
            """{"amount":-1,"spentOn":"2026/08/20","merchantName":"  ","tag":"FOOD"}""",
        )

        assertThat(result).isEqualTo(ReceiptOcrResult.EMPTY)
    }

    @Test
    fun `null과 빠진 필드는 null이다`() {
        assertThat(client.parse("""{"amount":null}""")).isEqualTo(ReceiptOcrResult.EMPTY)
    }
}
