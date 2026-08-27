package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.BASE_TIME
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReportCsvGeneratorTest {

    private val calculator = ReportSnapshotCalculator()
    private val generator = ReportCsvGenerator()

    @Test
    fun `지출 내역이 모두 포함된다`() {
        val csv = generate(
            sampleSpending(spendingId = 1L, description = "렌터카", amount = 240_000L, tag = "TRANSPORTATION", tagLabel = "교통비"),
            sampleSpending(spendingId = 2L, description = "저녁 식사", amount = 180_000L, tag = "MEAL", tagLabel = "식비"),
        )

        assertThat(csv).contains("렌터카", "저녁 식사")
        assertThat(csv).contains("240000", "180000")
    }

    @Test
    fun `태그는 한글 라벨로 출력된다`() {
        val csv = generate(sampleSpending(tag = "MEAL", tagLabel = "식비"))

        assertThat(csv).contains("식비")
        assertThat(csv).doesNotContain("MEAL")
    }

    @Test
    @DisplayName("항목명에 쉼표가 있어도 열이 밀리지 않는다")
    fun escapesComma() {
        val csv = generate(sampleSpending(description = "저녁식사, 술값", amount = 50_000L))

        // 따옴표로 감싸져야 한 칸으로 유지된다.
        assertThat(csv).contains("\"저녁식사, 술값\"")

        val row = csv.lines().first { it.contains("저녁식사") }
        assertThat(parseColumns(row)).hasSize(5)
    }

    @Test
    fun `항목명에 따옴표가 있어도 깨지지 않는다`() {
        val csv = generate(sampleSpending(description = "\"특선\" 코스"))

        val row = csv.lines().first { it.contains("특선") }
        assertThat(parseColumns(row)).hasSize(5)
    }

    @Test
    fun `지출이 없어도 헤더는 출력된다`() {
        val snapshot = calculator.calculate(sampleData(approvedSpendings = emptyList()))

        val csv = String(generator.generate(snapshot), Charsets.UTF_8)

        assertThat(csv).contains("일시,항목,태그,금액,결제자")
        assertThat(csv).contains("총 예산")
    }

    @Test
    @DisplayName("엑셀 한글 깨짐 방지를 위해 UTF-8 BOM이 붙는다")
    fun hasUtf8Bom() {
        val bytes = generator.generate(calculator.calculate(sampleData()))

        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }

    @Test
    @DisplayName("금액은 천단위 구분자 없는 정수로 출력된다")
    fun amountIsPlainInteger() {
        val csv = generate(sampleSpending(amount = 1_234_567L))

        assertThat(csv).contains("1234567")
        assertThat(csv).doesNotContain("1,234,567")
        assertThat(csv).doesNotContain("1234567.0")
    }

    @Test
    fun `요약에 총예산 총지출 잔액이 담긴다`() {
        val snapshot = calculator.calculate(
            sampleData(initialBudget = 500_000L, approvedSpendings = listOf(sampleSpending(amount = 320_000L))),
        )

        val csv = String(generator.generate(snapshot), Charsets.UTF_8)

        assertThat(csv).contains("총 예산,500000")
        assertThat(csv).contains("총 지출,320000")
        assertThat(csv).contains("남은 금액,180000")
    }

    @Test
    fun `참여 인원이 없으면 해당 줄을 넣지 않는다`() {
        val csv = String(
            generator.generate(calculator.calculate(sampleData(participantCount = null))),
            Charsets.UTF_8,
        )

        assertThat(csv).doesNotContain("참여 인원")
    }

    private fun generate(vararg spendings: com.moasem.backend.domain.report.service.port.ApprovedSpendingData): String {
        val snapshot = calculator.calculate(sampleData(approvedSpendings = spendings.toList()))
        return String(generator.generate(snapshot), Charsets.UTF_8)
    }

    /** 따옴표로 감싼 값 안의 쉼표를 무시하고 열 개수를 센다. */
    private fun parseColumns(row: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                c == '"' && inQuotes && i + 1 < row.length && row[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    columns.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        columns.add(current.toString())
        return columns
    }
}
