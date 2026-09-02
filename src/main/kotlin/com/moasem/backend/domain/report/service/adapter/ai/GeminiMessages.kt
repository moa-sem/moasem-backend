package com.moasem.backend.domain.report.service.adapter.ai

/**
 * Gemini generateContent 요청·응답 중 우리가 쓰는 부분만 정의한다.
 *
 * 응답에는 안전 등급, 인용 정보 등 훨씬 많은 필드가 오지만 매핑하지 않는다.
 * Spring Boot는 모르는 필드를 무시하도록 설정돼 있어, 스펙이 늘어나도 깨지지 않는다.
 */
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
) {
    data class Content(val parts: List<Part>)

    data class Part(val text: String)

    /**
     * 같은 결산에 대해 매번 크게 다른 총평이 나오면 재시도 시 혼란스럽다.
     * 그렇다고 0으로 두면 문장이 딱딱해져 중간값을 쓴다.
     */
    data class GenerationConfig(val temperature: Double)

    companion object {
        private const val TEMPERATURE = 0.4

        fun of(prompt: String) = GeminiRequest(
            contents = listOf(Content(listOf(Part(prompt)))),
            generationConfig = GenerationConfig(TEMPERATURE),
        )
    }
}

data class GeminiResponse(
    val candidates: List<Candidate>? = null,
) {
    data class Candidate(val content: Content? = null)

    data class Content(val parts: List<Part>? = null)

    data class Part(val text: String? = null)

    /**
     * 응답이 여러 조각으로 나뉘어 오는 경우가 있어 이어 붙인다.
     *
     * 안전 필터에 걸리면 candidates가 비거나 content가 없이 온다. 그때는 null을 반환해
     * 호출부가 실패로 처리하게 한다.
     */
    fun text(): String? = candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text }
        ?.joinToString("")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
