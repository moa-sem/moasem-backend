package com.moasem.backend.domain.report.service.port

/**
 * 테스트용 [ReportAiClient].
 *
 * 실제 AI를 호출하지 않으며, [failWith]로 실패 상황을 재현할 수 있다.
 * AI 실패가 보고서 생성을 실패시키지 않는지 검증할 때 쓴다.
 */
class FakeReportAiClient(
    private var response: String = DEFAULT_RESPONSE,
) : ReportAiClient {

    private var failure: ReportAiException? = null
    var lastInput: AiAnalysisInput? = null
        private set
    var callCount: Int = 0
        private set

    fun respondWith(text: String) {
        response = text
        failure = null
    }

    fun failWith(message: String = "AI 분석 실패") {
        failure = ReportAiException(message)
    }

    override fun analyze(input: AiAnalysisInput): String {
        callCount++
        lastInput = input
        failure?.let { throw it }
        return response
    }

    companion object {
        const val DEFAULT_RESPONSE = "예산의 64%를 사용했으며 식비 비중이 가장 높습니다."
    }
}
