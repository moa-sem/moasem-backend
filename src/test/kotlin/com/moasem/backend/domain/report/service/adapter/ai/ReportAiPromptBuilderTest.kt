package com.moasem.backend.domain.report.service.adapter.ai

import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import com.moasem.backend.domain.report.service.port.TagTotalData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 프롬프트 규칙을 고정한다.
 *
 * 규칙 한 줄이 빠져도 API 호출은 성공하고 출력만 조용히 나빠진다. 실제 호출 테스트로는
 * 잡히지 않아 여기서 문자열로 검증한다. 각 규칙은 검증 과정에서 실제로 관찰된 실패에
 * 대응하므로, 지우려면 그 실패가 재현되지 않는지 먼저 확인해야 한다.
 */
class ReportAiPromptBuilderTest {

    private val builder = ReportAiPromptBuilder()

    private fun input(
        participantCount: Int? = 12,
        remainingBalance: Long = 24_500L,
        tagTotals: List<TagTotalData> = listOf(
            TagTotalData(tag = "LODGING", label = "숙박비", amount = 420_000L, count = 1),
            TagTotalData(tag = "MEAL", label = "식비", amount = 260_500L, count = 2),
        ),
    ) = AiAnalysisInput(
        eventTitle = "여름 MT",
        participantCount = participantCount,
        totalBudget = 950_000L,
        totalSpent = 925_500L,
        remainingBalance = remainingBalance,
        tagTotals = tagTotals,
    )

    @Test
    @DisplayName("금액은 천 단위 구분 기호를 넣어 사람이 읽는 형태로 전달한다")
    fun formatsMoney() {
        val prompt = builder.build(input())

        assertThat(prompt).contains("[총예산] 950,000원")
        assertThat(prompt).contains("[총지출] 925,500원")
        assertThat(prompt).contains("- 숙박비: 420,000원 (1건)")
        assertThat(prompt).contains("- 식비: 260,500원 (2건)")
    }

    @Test
    @DisplayName("예산을 초과하면 잔액을 음수 그대로 전달한다")
    fun keepsNegativeBalance() {
        // 부호를 지우면 초과 상황인지 AI가 알 수 없다.
        val prompt = builder.build(input(remainingBalance = -112_000L))

        assertThat(prompt).contains("[잔액] -112,000원")
    }

    @Test
    @DisplayName("참여 인원이 없으면 해당 줄을 넣지 않는다")
    fun omitsMissingParticipantCount() {
        val prompt = builder.build(input(participantCount = null))

        assertThat(prompt).doesNotContain("참여 인원")
        assertThat(prompt).contains("[행사] 여름 MT")
    }

    @Test
    @DisplayName("관찰된 실패에 대응하는 규칙이 프롬프트에 들어 있다")
    fun containsObservedFailureRules() {
        val prompt = builder.build(input())

        // 금액을 "40万원"으로 쓰는 경우가 반복해서 나왔다.
        assertThat(prompt).contains("한자 수사")
        // 행사 기간, 장소 특성, 지출 원인을 지어내는 경우가 나왔다.
        assertThat(prompt).contains("제공되지 않은 사실을 추측하거나 지어내지 않는다")
        // 비율 계산은 틀린 적이 없어 허용한다. 막으면 숫자만 되읊는 총평이 된다.
        assertThat(prompt).contains("집행률과 항목별 비중은 계산해서 밝힌다")
    }

    @Test
    @DisplayName("개별 지출 내역과 결제자 이름은 애초에 전달할 수 없다")
    fun carriesAggregatesOnly() {
        // AiAnalysisInput에 개별 지출이 없어 프롬프트에도 들어갈 수 없다.
        // 무료 티어는 입력이 모델 개선에 쓰일 수 있어, 나가는 범위를 좁게 유지한다.
        val prompt = builder.build(input())

        assertThat(prompt).doesNotContain("결제자")
        // 규칙 목록에도 "- "로 시작하는 줄이 있어, 지출 항목 형태만 세어 확인한다.
        val spendingLines = Regex("""^- .+: [\d,]+원 \(\d+건\)$""", RegexOption.MULTILINE)
        assertThat(spendingLines.findAll(prompt).count()).isEqualTo(2)
    }
}
