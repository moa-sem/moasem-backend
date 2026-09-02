package com.moasem.backend.domain.report.service.adapter.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 응답 파싱.
 *
 * Gemini는 정상 응답 외에 안전 필터에 걸린 응답, 조각난 응답 등 여러 형태를 돌려준다.
 * 어느 경우든 null을 돌려주면 호출부가 실패로 처리하므로, 여기서 형태별로 고정한다.
 */
class GeminiResponseTest {

    private fun response(vararg texts: String?) = GeminiResponse(
        candidates = listOf(
            GeminiResponse.Candidate(
                content = GeminiResponse.Content(parts = texts.map { GeminiResponse.Part(it) }),
            ),
        ),
    )

    @Test
    fun `여러 조각으로 나뉘어 오면 이어 붙인다`() {
        assertThat(response("이번 여름 MT는 ", "97.4%를 집행했습니다.").text())
            .isEqualTo("이번 여름 MT는 97.4%를 집행했습니다.")
    }

    @Test
    fun `앞뒤 공백은 잘라낸다`() {
        assertThat(response("\n  총평입니다.  \n").text()).isEqualTo("총평입니다.")
    }

    @Test
    @DisplayName("안전 필터에 걸리면 candidates가 비어 오고, 이때는 null이다")
    fun blockedResponse() {
        assertThat(GeminiResponse(candidates = emptyList()).text()).isNull()
        assertThat(GeminiResponse(candidates = null).text()).isNull()
    }

    @Test
    @DisplayName("본문 없이 껍데기만 오면 null이다")
    fun emptyShapes() {
        assertThat(GeminiResponse(listOf(GeminiResponse.Candidate(content = null))).text()).isNull()
        assertThat(response().text()).isNull()
        assertThat(response(null).text()).isNull()
        assertThat(response("   ").text()).isNull()
    }

    @Test
    @DisplayName("요청은 프롬프트를 그대로 담고 temperature를 고정한다")
    fun requestShape() {
        val request = GeminiRequest.of("프롬프트")

        assertThat(request.contents).hasSize(1)
        assertThat(request.contents[0].parts[0].text).isEqualTo("프롬프트")
        // 재시도 때마다 총평이 크게 달라지지 않도록 낮게 유지한다.
        assertThat(request.generationConfig.temperature).isEqualTo(0.4)
    }
}
