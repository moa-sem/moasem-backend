package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.BASE_TIME
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReportPdfGeneratorTest {

    private val calculator = ReportSnapshotCalculator()
    private val generator = ReportPdfGenerator()

    @Test
    @DisplayName("PDF 형식의 바이트가 생성된다")
    fun producesPdfBytes() {
        val bytes = generator.generate(calculator.calculate(sampleData()))

        // PDF 파일은 반드시 %PDF- 로 시작한다.
        assertThat(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)).isEqualTo("%PDF-")
        assertThat(bytes.size).isGreaterThan(1000)
    }

    @Test
    fun `AI 분석이 있으면 포함해서 생성한다`() {
        val snapshot = calculator.calculate(sampleData())

        val withAi = generator.generate(snapshot, aiSummary = "예산의 64%를 사용했으며 식비 비중이 가장 높습니다.")
        val withoutAi = generator.generate(snapshot, aiSummary = null)

        // AI 섹션이 들어가면 문서가 더 커진다.
        assertThat(withAi.size).isGreaterThan(withoutAi.size)
    }

    @Test
    @DisplayName("AI 분석이 실패해도 PDF는 정상 생성된다")
    fun generatesWithoutAi() {
        val bytes = generator.generate(calculator.calculate(sampleData()), aiSummary = null)

        assertThat(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)).isEqualTo("%PDF-")
    }

    @Test
    fun `지출이 없어도 생성된다`() {
        val snapshot = calculator.calculate(sampleData(approvedSpendings = emptyList()))

        assertThatCode { generator.generate(snapshot) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("지출이 많아 여러 페이지가 되어도 생성된다")
    fun handlesManySpendings() {
        val spendings = (1..80).map {
            sampleSpending(
                spendingId = it.toLong(),
                description = "지출 항목 $it",
                amount = 10_000L,
                spentAt = BASE_TIME.plusHours(it.toLong()),
            )
        }
        val snapshot = calculator.calculate(sampleData(approvedSpendings = spendings))

        val bytes = generator.generate(snapshot, aiSummary = "여러 페이지 테스트")

        assertThat(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)).isEqualTo("%PDF-")
        assertThat(snapshot.spendings).hasSize(80)
    }

    @Test
    @DisplayName("한글이 포함되어도 폰트 예외가 발생하지 않는다")
    fun rendersKorean() {
        val snapshot = calculator.calculate(
            sampleData(
                approvedSpendings = listOf(
                    sampleSpending(description = "삼겹살집 저녁 회식", payerName = "김석주", tagLabel = "식비"),
                ),
            ),
        )

        assertThatCode { generator.generate(snapshot, aiSummary = "한글 분석 코멘트입니다.") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `예산을 초과해도 생성된다`() {
        val snapshot = calculator.calculate(
            sampleData(
                initialBudget = 100_000L,
                approvedSpendings = listOf(sampleSpending(amount = 150_000L)),
            ),
        )

        assertThat(snapshot.budget.remainingBalance).isNegative()
        assertThatCode { generator.generate(snapshot) }.doesNotThrowAnyException()
    }

    @Test
    fun `참여 인원이 없어도 생성된다`() {
        val snapshot = calculator.calculate(sampleData(participantCount = null))

        assertThatCode { generator.generate(snapshot) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("같은 스냅샷으로 여러 번 생성해도 폰트 재사용에 문제가 없다")
    fun reusesFontAcrossDocuments() {
        val snapshot = calculator.calculate(sampleData())

        val results: List<ByteArray> = (1..3).map { generator.generate(snapshot) }

        results.forEach {
            assertThat(String(it.copyOfRange(0, 5), Charsets.US_ASCII)).isEqualTo("%PDF-")
        }
    }
}
