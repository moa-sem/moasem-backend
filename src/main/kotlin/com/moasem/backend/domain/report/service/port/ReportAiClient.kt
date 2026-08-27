package com.moasem.backend.domain.report.service.port

/**
 * 확정된 결산 수치를 해석해 사람이 읽을 분석 코멘트를 생성한다.
 *
 * 실패는 예외로 알린다. 반환값에 실패를 섞지 않는 이유는, AI 실패가 보고서 실패가 아니라는
 * 점을 호출부가 명시적으로 처리하도록 강제하기 위해서다.
 * 호출부는 예외를 잡아 `Report.failAiAnalysis()`를 호출하고 파일 생성은 계속 진행한다.
 */
interface ReportAiClient {

    /**
     * @return 분석 코멘트 텍스트. 금액을 포함하지 않는다.
     * @throws ReportAiException 분석에 실패한 경우
     */
    fun analyze(input: AiAnalysisInput): String
}

/**
 * AI에게 전달할 입력.
 *
 * **개별 지출 내역을 담지 않는다.** 이미 계산이 끝난 집계값만 전달해 AI가 금액을
 * 다시 계산할 재료 자체를 주지 않는다. 프롬프트로 부탁하는 대신 시그니처로 막는 것이다.
 */
data class AiAnalysisInput(
    val eventTitle: String,
    val totalBudget: Long,
    val totalSpent: Long,
    val remainingBalance: Long,
    val tagTotals: List<TagTotalData>,
)

data class TagTotalData(
    val tag: String,
    val label: String,
    val amount: Long,
    val count: Int,
)

class ReportAiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
