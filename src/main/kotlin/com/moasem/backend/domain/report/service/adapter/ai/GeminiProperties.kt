package com.moasem.backend.domain.report.service.adapter.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Gemini 호출 설정.
 *
 * [apiKey]는 환경변수 `GEMINI_API_KEY`에서만 들어온다. 설정 파일에는 값이 아니라
 * 이름만 적는다. 키가 비어 있으면 어댑터 자체가 등록되지 않고 스텁이 대신 쓰인다.
 */
@ConfigurationProperties(prefix = "moasem.ai.gemini")
data class GeminiProperties(
    val apiKey: String = "",

    /**
     * 실제 키로 확인한 결과를 반영한 기본값이다.
     * 2.5 계열은 신규 사용자에게 막혀 있고, 3.7-flash는 90초 안에 응답하지 않았다.
     * flash-lite는 2초 내외로 답한다.
     */
    val model: String = "gemini-3.5-flash-lite",

    val baseUrl: String = "https://generativelanguage.googleapis.com",

    /** 보고서 생성 전체를 붙잡고 있으므로 길게 두지 않는다. */
    val timeout: Duration = Duration.ofSeconds(30),

    val connectTimeout: Duration = Duration.ofSeconds(5),
) {
    /**
     * 키를 가린다.
     *
     * data class의 기본 toString은 모든 필드를 그대로 찍는다. 이 객체가 어딘가에서
     * 로깅되면 키가 로그에 남고, 로그는 보통 파일이나 수집 시스템으로 흘러간다.
     */
    override fun toString() =
        "GeminiProperties(apiKey=${if (apiKey.isBlank()) "없음" else "설정됨"}, " +
            "model=$model, baseUrl=$baseUrl, timeout=$timeout, connectTimeout=$connectTimeout)"
}
