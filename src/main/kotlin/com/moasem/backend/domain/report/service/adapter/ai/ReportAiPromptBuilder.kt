package com.moasem.backend.domain.report.service.adapter.ai

import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import org.springframework.stereotype.Component
import java.text.DecimalFormat

/**
 * AI에 보낼 프롬프트를 조립한다.
 *
 * 호출 코드와 분리한 이유는 규칙을 테스트로 고정하기 위해서다. 프롬프트 한 줄이 빠지면
 * 출력이 조용히 나빠지는데, 실제 호출 테스트로는 잡히지 않는다.
 *
 * 규칙은 실제로 관찰한 실패만 담았다. 상상으로 규칙을 늘리면 출력이 굳어지기만 한다.
 * - `40万원`처럼 한자 수사로 금액을 쓰는 경우가 반복됐다.
 * - 행사 기간, 장소 특성, 지출 원인처럼 주지 않은 사실을 지어냈다.
 *
 * 반면 비율·집행률 계산은 검증 과정에서 틀린 적이 없어 허용한다. 계산을 막으면
 * 숫자를 다시 읊는 수준의 총평만 나온다.
 */
@Component
class ReportAiPromptBuilder {

    fun build(input: AiAnalysisInput): String = buildString {
        appendLine(RULES)
        appendLine("[행사] ${input.eventTitle}")
        input.participantCount?.let { appendLine("[참여 인원] ${it}명") }
        appendLine("[총예산] ${money(input.totalBudget)}")
        appendLine("[총지출] ${money(input.totalSpent)}")
        appendLine("[잔액] ${money(input.remainingBalance)}")
        appendLine("[태그별 지출]")
        input.tagTotals.forEach {
            appendLine("- ${it.label}: ${money(it.amount)} (${it.count}건)")
        }
    }

    /** 예산을 초과하면 잔액이 음수다. 부호를 그대로 보여줘 초과 상황임을 드러낸다. */
    private fun money(amount: Long) = "${MONEY_FORMAT.format(amount)}원"

    companion object {
        private val MONEY_FORMAT = DecimalFormat("#,##0")

        private val RULES = """
            동아리 행사 결산 보고서에 들어갈 총평을 쓴다.

            작성 규칙:
            - 한국어 4~5문장, 존댓말.
            - 숫자는 아라비아 숫자와 "원"으로만 표기한다. 万, 億 같은 한자 수사를 쓰지 않는다.
            - 비율은 "%" 기호로 표기한다. 소수점 첫째 자리까지.
            - 집행률과 항목별 비중은 계산해서 밝힌다.
            - 아래 제공된 정보만 사용한다. 장소, 기간, 날씨, 물가, 지출 원인, 인원 변동 등
              제공되지 않은 사실을 추측하거나 지어내지 않는다.
            - 제안을 한 가지 덧붙이되, 제공된 수치에서 직접 도출되는 것만 쓴다.

        """.trimIndent()
    }
}
