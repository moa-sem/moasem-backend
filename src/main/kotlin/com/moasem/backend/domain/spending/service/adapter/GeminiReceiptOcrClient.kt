package com.moasem.backend.domain.spending.service.adapter

import com.moasem.backend.domain.report.service.adapter.ai.GeminiProperties
import com.moasem.backend.domain.report.service.adapter.ai.GeminiResponse
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.service.port.ReceiptOcrClient
import com.moasem.backend.domain.spending.service.port.ReceiptOcrResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.Base64

/**
 * Gemini 멀티모달로 영수증을 읽는다.
 *
 * 일반 OCR은 글자만 뽑아 줘서 "어느 숫자가 총액인가"를 영수증 형식마다 규칙으로 풀어야 한다.
 * Gemini에 이미지와 응답 스키마를 함께 주면 읽기와 해석이 한 번에 끝난다.
 *
 * 결산 총평과 같은 키·모델 설정([GeminiProperties])을 쓴다. 키가 없으면 등록되지 않고
 * [com.moasem.backend.global.stub.LocalPortStubs]의 스텁이 쓰인다.
 *
 * **영수증 원본 이미지가 Gemini로 나간다.** 결산 총평은 집계값만 보내지만 이 기능은 다르다.
 */
@ConditionalOnExpression("!'\${moasem.ai.gemini.api-key:}'.isEmpty()")
@EnableConfigurationProperties(GeminiProperties::class)
@Component
class GeminiReceiptOcrClient(
    private val properties: GeminiProperties,
    private val objectMapper: ObjectMapper,
) : ReceiptOcrClient {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.timeout)
            },
        )
        .defaultHeader(API_KEY_HEADER, properties.apiKey)
        .build()

    override fun recognize(image: ByteArray, mimeType: String): ReceiptOcrResult {
        val text = restClient.post()
            .uri("/v1beta/models/{model}:generateContent", properties.model)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody(image, mimeType))
            .retrieve()
            .body(GeminiResponse::class.java)
            ?.text()
            ?: return ReceiptOcrResult.EMPTY

        return parse(text)
    }

    /**
     * 모델이 준 JSON을 결과로 바꾼다.
     *
     * 스키마를 지정해도 형식이 어긋난 값이 올 수 있다. 날짜가 깨졌거나 없는 태그가 오면
     * 그 필드만 비우고 나머지는 살린다.
     */
    internal fun parse(json: String): ReceiptOcrResult {
        val raw = objectMapper.readValue(json, RawResult::class.java)
        return ReceiptOcrResult(
            amount = raw.amount?.takeIf { it > 0 },
            spentOn = raw.spentOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            merchantName = raw.merchantName?.trim()?.takeIf { it.isNotBlank() },
            tag = raw.tag?.let { name -> SpendingTag.entries.find { it.name == name } },
        )
    }

    internal data class RawResult(
        val amount: Long? = null,
        val spentOn: String? = null,
        val merchantName: String? = null,
        val tag: String? = null,
    )

    private fun requestBody(image: ByteArray, mimeType: String) = mapOf(
        "contents" to listOf(
            mapOf(
                "parts" to listOf(
                    mapOf("inlineData" to mapOf("mimeType" to mimeType, "data" to Base64.getEncoder().encodeToString(image))),
                    mapOf("text" to PROMPT),
                ),
            ),
        ),
        // 같은 영수증은 매번 같은 값이 나와야 한다.
        "generationConfig" to mapOf(
            "temperature" to 0,
            "responseMimeType" to "application/json",
            "responseSchema" to RESPONSE_SCHEMA,
        ),
    )

    companion object {
        private const val API_KEY_HEADER = "x-goog-api-key"

        private val TAG_GUIDE = SpendingTag.entries.joinToString(", ") { "${it.name}(${it.label})" }

        private val PROMPT = """
            이 이미지는 영수증, 카드 전표 또는 결제 내역 캡처다. 지출 신청서에 채울 값을 뽑아라.
            - amount: 실제로 결제된 최종 총액(원, 정수). 받은 금액·거스름돈·부가세 단독 금액이 아니다.
            - spentOn: 결제일. YYYY-MM-DD.
            - merchantName: 상호명.
            - tag: 지출 분류. $TAG_GUIDE 중 하나.
            이미지에서 확실히 읽을 수 없는 값은 추측하지 말고 null로 둬라.
            영수증이 아니면 모든 값을 null로 둬라.
        """.trimIndent()

        private val RESPONSE_SCHEMA = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "amount" to mapOf("type" to "INTEGER", "nullable" to true),
                "spentOn" to mapOf("type" to "STRING", "nullable" to true),
                "merchantName" to mapOf("type" to "STRING", "nullable" to true),
                "tag" to mapOf("type" to "STRING", "nullable" to true, "enum" to SpendingTag.entries.map { it.name }),
            ),
        )
    }
}
