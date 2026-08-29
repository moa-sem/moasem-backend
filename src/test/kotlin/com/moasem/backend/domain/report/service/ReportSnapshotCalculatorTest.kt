package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.BASE_TIME
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleAddition
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import com.moasem.backend.global.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ReportSnapshotCalculatorTest {

    private val calculator = ReportSnapshotCalculator()

    @Nested
    @DisplayName("금액 계산")
    inner class Amounts {

        @Test
        fun `총예산은 최초 예산과 추가 예산의 합이다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    initialBudget = 500_000L,
                    budgetAdditions = listOf(
                        sampleAddition(amount = 100_000L),
                        sampleAddition(amount = 50_000L),
                    ),
                ),
            )

            assertThat(snapshot.budget.totalBudget).isEqualTo(650_000L)
        }

        @Test
        fun `총지출은 승인된 지출의 합이다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    approvedSpendings = listOf(
                        sampleSpending(spendingId = 1L, amount = 120_000L),
                        sampleSpending(spendingId = 2L, amount = 80_000L),
                    ),
                ),
            )

            assertThat(snapshot.budget.totalSpent).isEqualTo(200_000L)
        }

        @Test
        fun `잔액은 총예산에서 총지출을 뺀 값이다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    initialBudget = 500_000L,
                    approvedSpendings = listOf(sampleSpending(amount = 320_000L)),
                ),
            )

            assertThat(snapshot.budget.remainingBalance).isEqualTo(180_000L)
        }

        @Test
        fun `지출이 하나도 없으면 잔액은 총예산과 같다`() {
            val snapshot = calculator.calculate(
                sampleData(initialBudget = 500_000L, approvedSpendings = emptyList()),
            )

            assertThat(snapshot.budget.totalSpent).isZero()
            assertThat(snapshot.budget.remainingBalance).isEqualTo(500_000L)
            assertThat(snapshot.spendings).isEmpty()
            assertThat(snapshot.tagTotals).isEmpty()
        }

        @Test
        @DisplayName("예산을 초과하면 잔액이 음수가 된다")
        fun negativeBalance() {
            val snapshot = calculator.calculate(
                sampleData(
                    initialBudget = 100_000L,
                    approvedSpendings = listOf(sampleSpending(amount = 150_000L)),
                ),
            )

            // 0으로 막지 않는다. 초과했다는 사실이 보고서에 드러나야 한다.
            assertThat(snapshot.budget.remainingBalance).isEqualTo(-50_000L)
        }
    }

    @Nested
    @DisplayName("태그별 집계")
    inner class TagTotals {

        @Test
        fun `같은 태그의 지출은 하나로 묶이고 건수가 집계된다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    approvedSpendings = listOf(
                        sampleSpending(spendingId = 1L, amount = 30_000L, tag = "MEAL", tagLabel = "식비"),
                        sampleSpending(spendingId = 2L, amount = 20_000L, tag = "MEAL", tagLabel = "식비"),
                        sampleSpending(spendingId = 3L, amount = 90_000L, tag = "TRANSPORTATION", tagLabel = "교통비"),
                    ),
                ),
            )

            val meal = snapshot.tagTotals.first { it.tag == "MEAL" }
            assertThat(meal.amount).isEqualTo(50_000L)
            assertThat(meal.count).isEqualTo(2)
            assertThat(meal.label).isEqualTo("식비")
        }

        @Test
        @DisplayName("태그별 합계의 총합은 총지출과 일치한다")
        fun tagTotalsSumMatchesTotalSpent() {
            val snapshot = calculator.calculate(
                sampleData(
                    approvedSpendings = listOf(
                        sampleSpending(spendingId = 1L, amount = 30_000L, tag = "MEAL"),
                        sampleSpending(spendingId = 2L, amount = 90_000L, tag = "TRANSPORTATION"),
                        sampleSpending(spendingId = 3L, amount = 45_000L, tag = "SUPPLIES"),
                    ),
                ),
            )

            // 이 둘이 어긋나면 보고서의 표 합계가 맞지 않는다.
            assertThat(snapshot.tagTotals.sumOf { it.amount }).isEqualTo(snapshot.budget.totalSpent)
        }

        @Test
        fun `금액이 큰 태그가 먼저 온다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    approvedSpendings = listOf(
                        sampleSpending(spendingId = 1L, amount = 30_000L, tag = "MEAL"),
                        sampleSpending(spendingId = 2L, amount = 90_000L, tag = "TRANSPORTATION"),
                        sampleSpending(spendingId = 3L, amount = 45_000L, tag = "SUPPLIES"),
                    ),
                ),
            )

            assertThat(snapshot.tagTotals.map { it.tag })
                .containsExactly("TRANSPORTATION", "SUPPLIES", "MEAL")
        }
    }

    @Nested
    @DisplayName("정렬과 전달")
    inner class OrderingAndMapping {

        @Test
        fun `지출 내역은 지출 일시 순으로 정렬된다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    approvedSpendings = listOf(
                        sampleSpending(spendingId = 3L, spentAt = BASE_TIME.plusDays(3)),
                        sampleSpending(spendingId = 1L, spentAt = BASE_TIME.plusDays(1)),
                        sampleSpending(spendingId = 2L, spentAt = BASE_TIME.plusDays(2)),
                    ),
                ),
            )

            assertThat(snapshot.spendings.map { it.spendingId }).containsExactly(1L, 2L, 3L)
        }

        @Test
        fun `예산 추가 내역은 등록 시각 순으로 정렬되고 등록자가 보존된다`() {
            val snapshot = calculator.calculate(
                sampleData(
                    budgetAdditions = listOf(
                        sampleAddition(amount = 50_000L, addedBy = "이한별", addedAt = BASE_TIME.plusHours(5)),
                        sampleAddition(amount = 100_000L, addedBy = "김소담", addedAt = BASE_TIME.plusHours(2)),
                    ),
                ),
            )

            assertThat(snapshot.budget.additions.map { it.addedBy }).containsExactly("김소담", "이한별")
        }

        @Test
        fun `참여 인원이 없어도 계산된다`() {
            val snapshot = calculator.calculate(sampleData(participantCount = null))

            assertThat(snapshot.event.participantCount).isNull()
            assertThat(snapshot.budget.totalBudget).isEqualTo(500_000L)
        }

        @Test
        fun `참여 인원이 있으면 스냅샷에 담긴다`() {
            val snapshot = calculator.calculate(sampleData(participantCount = 8))

            assertThat(snapshot.event.participantCount).isEqualTo(8)
        }
    }

    @Nested
    @DisplayName("사전 조건")
    inner class Preconditions {

        @Test
        fun `마감되지 않은 행사는 결산할 수 없다`() {
            assertThatThrownBy { calculator.calculate(sampleData(status = "ACTIVE")) }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("마감된 행사만")
        }
    }
}
