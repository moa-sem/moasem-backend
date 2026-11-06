package com.moasem.backend.domain.report.service

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.BASE_TIME
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleAddition
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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

    /**
     * 보이는 내용을 고정한다.
     *
     * 위의 테스트는 "만들어지는가"만 본다. 여기서는 읽는 사람이 보는 글자를 꺼내 확인한다.
     * 각 항목은 실제로 문제가 됐던 표시들이다.
     */
    @Nested
    @DisplayName("표시 내용")
    inner class Content {

        private fun textOf(bytes: ByteArray): String =
            PdfDocument(PdfReader(bytes.inputStream())).use { pdf ->
                (1..pdf.numberOfPages).joinToString(" ") { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
            }

        @Test
        @DisplayName("예산을 넘기면 잔액을 초과 금액으로 표시한다")
        fun labelsOverBudget() {
            val over = calculator.calculate(
                sampleData(initialBudget = 100_000L, approvedSpendings = listOf(sampleSpending(amount = 150_000L))),
            )

            val text = textOf(generator.generate(over))

            // 음수를 "남은 금액"이라고 부르면 예산을 넘긴 사실이 드러나지 않는다.
            assertThat(text).contains("초과 금액").doesNotContain("남은 금액")
        }

        @Test
        fun `예산 안이면 남은 금액으로 표시한다`() {
            val text = textOf(generator.generate(calculator.calculate(sampleData())))

            assertThat(text).contains("남은 금액").doesNotContain("초과 금액")
        }

        @Test
        @DisplayName("지출은 날짜만 표시하고 시각을 찍지 않는다")
        fun showsDateOnly() {
            // 지출은 날짜만 입력받아 시각이 항상 자정이다. 시각을 찍으면 모든 행이 00:00으로 나온다.
            val midnight = sampleSpending(spentAt = BASE_TIME.toLocalDate().atStartOfDay())
            val text = textOf(generator.generate(calculator.calculate(sampleData(approvedSpendings = listOf(midnight)))))

            assertThat(text).doesNotContainPattern("""\d{2}:\d{2}""")
        }

        @Test
        @DisplayName("예산 추가 내역이 사유와 함께 나온다")
        fun showsBudgetAdditions() {
            val snapshot = calculator.calculate(
                sampleData(budgetAdditions = listOf(sampleAddition(reason = "숙소 추가 예약", amount = 100_000L))),
            )

            val text = textOf(generator.generate(snapshot))

            assertThat(text).contains("예산 내역", "최초 예산", "숙소 추가 예약", "+100,000원")
        }

        @Test
        fun `항목별 비중과 집행률이 나온다`() {
            val text = textOf(generator.generate(calculator.calculate(sampleData())))

            assertThat(text).contains("비중", "집행률", "%")
        }

        @Test
        @DisplayName("모든 페이지에 전체 쪽수와 함께 페이지 번호가 찍힌다")
        fun numbersEveryPage() {
            val many = (1..80).map { sampleSpending(spendingId = it.toLong(), amount = 1_000L * it) }
            val bytes = generator.generate(calculator.calculate(sampleData(approvedSpendings = many)))

            PdfDocument(PdfReader(bytes.inputStream())).use { pdf ->
                val total = pdf.numberOfPages
                assertThat(total).isGreaterThan(1)
                (1..total).forEach { page ->
                    assertThat(PdfTextExtractor.getTextFromPage(pdf.getPage(page))).contains("$page / $total")
                }
            }
        }

        @Test
        @DisplayName("한글 단어를 중간에서 끊지 않는다")
        fun keepsKoreanWordsTogether() {
            // 이전에는 이 문장에서 "교통비가"가 "교" / "통비가"로 갈렸다.
            val ai = "이번 가을 워크숍에 총 10명이 참여하였으며, 총예산 1,100,000원에 총지출 1,500,000원이 " +
                "발생하여 집행률은 136.4%를 기록하였습니다. 이로 인해 400,000원의 적자가 발생하여 잔액은 " +
                "-400,000원입니다. 태그별 지출 비중은 교통비가 80.0%, 숙박비가 20.0%를 차지하였습니다."

            val text = textOf(generator.generate(calculator.calculate(sampleData()), aiSummary = ai))

            assertThat(text).contains("교통비가")
        }
    }
}
