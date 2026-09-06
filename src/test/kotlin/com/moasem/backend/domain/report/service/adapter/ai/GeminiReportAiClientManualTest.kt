package com.moasem.backend.domain.report.service.adapter.ai

import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import com.moasem.backend.domain.report.service.port.TagTotalData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * 실제 Gemini API를 호출한다.
 *
 * 프롬프트를 고칠 때 출력이 실제로 어떻게 달라지는지 확인하는 용도다.
 *
 * 실행하려면 `GEMINI_MANUAL_TEST=true` 를 함께 줘야 한다. 키가 있다는 이유만으로
 * 돌게 두면, 키를 교체했거나 만료된 사람의 빌드가 401로 깨진다. 실제로 그런 일이 있었다.
 * 외부 서비스 상태에 따라 결과가 달라지는 검사는 일반 빌드에 섞이지 않아야 한다.
 *
 *     GEMINI_MANUAL_TEST=true ./gradlew test --tests "*GeminiReportAiClientManualTest*"
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_MANUAL_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiReportAiClientManualTest {

    private val client = GeminiReportAiClient(
        properties = GeminiProperties(apiKey = System.getenv("GEMINI_API_KEY")),
        promptBuilder = ReportAiPromptBuilder(),
    )

    @Test
    @DisplayName("예산 초과 결산에서 숫자를 정확히 인용하고 없는 사실을 지어내지 않는다")
    fun overBudget() {
        val summary = client.analyze(
            AiAnalysisInput(
                eventTitle = "가을 워크숍",
                participantCount = 9,
                totalBudget = 400_000L,
                totalSpent = 512_000L,
                remainingBalance = -112_000L,
                tagTotals = listOf(
                    TagTotalData(tag = "MEAL", label = "식비", amount = 380_000L, count = 5),
                    TagTotalData(tag = "TRANSPORT", label = "교통비", amount = 132_000L, count = 2),
                ),
            ),
        )

        println("\n===== AI 총평 =====\n$summary\n")

        assertThat(summary).isNotBlank()
        // 반복해서 나왔던 한자 수사 표기
        assertThat(summary).doesNotContain("万", "億")
        // 주어진 금액을 그대로 인용해야 한다
        assertThat(summary).contains("512,000원")
    }
}
