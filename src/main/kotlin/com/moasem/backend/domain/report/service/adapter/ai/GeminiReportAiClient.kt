package com.moasem.backend.domain.report.service.adapter.ai

import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import com.moasem.backend.domain.report.service.port.ReportAiClient
import com.moasem.backend.domain.report.service.port.ReportAiException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Gemini API로 결산 총평을 생성한다.
 *
 * 공식 SDK 대신 [RestClient]를 쓴다. 호출이 한 종류뿐이라 SDK가 해 줄 일이 거의 없는데,
 * SDK를 넣으면 guava·protobuf가 따라온다. 이 프로젝트는 Jackson 2와 3이 이미 공존하고
 * 있어 의존성을 더 늘리지 않는 편이 낫다.
 *
 * 키가 비어 있으면 이 빈은 등록되지 않고 [com.moasem.backend.global.stub.LocalPortStubs]의
 * 스텁이 쓰인다. 팀원이 키 없이도 앱을 띄울 수 있어야 하기 때문이다.
 *
 * AI에 나가는 값은 집계값뿐이다. 결제자 이름과 개별 지출 내역은 [AiAnalysisInput]에
 * 애초에 담기지 않는다.
 */
@ConditionalOnExpression("!'\${moasem.ai.gemini.api-key:}'.isEmpty()")
@EnableConfigurationProperties(GeminiProperties::class)
@Component
class GeminiReportAiClient(
    private val properties: GeminiProperties,
    private val promptBuilder: ReportAiPromptBuilder,
) : ReportAiClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.timeout)
            },
        )
        // 키를 URL 쿼리로 넘기면 로그·프록시에 그대로 남는다. 헤더로 보낸다.
        .defaultHeader(API_KEY_HEADER, properties.apiKey)
        .build()

    override fun analyze(input: AiAnalysisInput): String {
        val prompt = promptBuilder.build(input)

        val response = try {
            restClient.post()
                .uri("/v1beta/models/{model}:generateContent", properties.model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GeminiRequest.of(prompt))
                .retrieve()
                .body(GeminiResponse::class.java)
        } catch (e: RestClientException) {
            // 예외 메시지에 요청 본문이 섞여 나갈 수 있어 그대로 노출하지 않는다.
            log.warn("Gemini 호출 실패. model={}", properties.model, e)
            throw ReportAiException("AI 분석 요청에 실패했습니다.", e)
        }

        return response?.text()
            ?: throw ReportAiException("AI 분석 응답이 비어 있습니다.")
    }

    companion object {
        private const val API_KEY_HEADER = "x-goog-api-key"
    }
}
