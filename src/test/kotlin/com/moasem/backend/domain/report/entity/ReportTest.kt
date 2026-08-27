package com.moasem.backend.domain.report.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReportTest {

    @Nested
    @DisplayName("보고서 생성")
    inner class Create {

        @Test
        fun `생성 직후에는 아무것도 계산되지 않은 상태다`() {
            val report = Report.create(EVENT_ID)

            assertThat(report.eventId).isEqualTo(EVENT_ID)
            assertThat(report.status).isEqualTo(ReportStatus.PENDING)
            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.PENDING)
            assertThat(report.snapshot).isNull()
            assertThat(report.retryCount).isZero()
            assertThat(report.generatedAt).isNull()
            assertThat(report.isDownloadable).isFalse()
        }
    }

    @Nested
    @DisplayName("결산 스냅샷")
    inner class Snapshot {

        @Test
        fun `스냅샷을 확정하면 조회용 금액 필드가 함께 채워진다`() {
            val report = Report.create(EVENT_ID)

            report.applySnapshot(snapshot(totalBudget = 500_000L, totalSpent = 320_000L))

            assertThat(report.totalBudget).isEqualTo(500_000L)
            assertThat(report.totalSpent).isEqualTo(320_000L)
            assertThat(report.remainingBalance).isEqualTo(180_000L)
        }

        @Test
        fun `한 번 확정한 스냅샷은 다시 바꿀 수 없다`() {
            val report = Report.create(EVENT_ID)
            report.applySnapshot(snapshot(totalBudget = 500_000L, totalSpent = 320_000L))

            assertThatThrownBy { report.applySnapshot(snapshot(totalBudget = 999L, totalSpent = 0L)) }
                .isInstanceOf(IllegalStateException::class.java)

            // 재다운로드 시 동일한 결산 결과를 보장해야 하므로 기존 값이 유지되어야 한다.
            assertThat(report.totalBudget).isEqualTo(500_000L)
        }
    }

    @Nested
    @DisplayName("AI 분석")
    inner class AiAnalysis {

        @Test
        fun `AI 분석에 실패해도 보고서 생성 상태는 바뀌지 않는다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()

            report.failAiAnalysis()

            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.FAILED)
            assertThat(report.status).isEqualTo(ReportStatus.GENERATING)
            assertThat(report.aiSummary).isNull()
        }

        @Test
        fun `AI가 실패해도 기본 PDF·CSV가 있으면 보고서는 완료된다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()
            report.applySnapshot(snapshot(totalBudget = 500_000L, totalSpent = 320_000L))
            report.failAiAnalysis()

            report.complete(pdfFileKey = PDF_KEY, csvFileKey = CSV_KEY)

            assertThat(report.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.FAILED)
            assertThat(report.isDownloadable).isTrue()
        }

        @Test
        fun `AI 분석에 성공하면 요약문이 저장된다`() {
            val report = Report.create(EVENT_ID)

            report.completeAiAnalysis("예산의 64%를 사용했으며 식비 비중이 가장 높습니다.")

            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.SUCCEEDED)
            assertThat(report.aiSummary).isNotNull()
        }
    }

    @Nested
    @DisplayName("상태 전이")
    inner class StatusTransition {

        @Test
        fun `완료되면 다운로드 가능해지고 생성 시각이 기록된다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()

            report.complete(pdfFileKey = PDF_KEY, csvFileKey = CSV_KEY)

            assertThat(report.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(report.generatedAt).isNotNull()
            assertThat(report.isDownloadable).isTrue()
        }

        @Test
        fun `실패하면 재시도 대상이 된다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()

            report.fail("PDF 생성 중 오류")

            assertThat(report.status).isEqualTo(ReportStatus.FAILED)
            assertThat(report.status.isRetryable).isTrue()
            assertThat(report.failureReason).isEqualTo("PDF 생성 중 오류")
        }

        @Test
        fun `실패한 보고서는 다시 생성을 시작할 수 있고 이전 실패 사유는 지워진다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()
            report.fail("PDF 생성 중 오류")

            report.startGenerating()
            report.increaseRetryCount()

            assertThat(report.status).isEqualTo(ReportStatus.GENERATING)
            assertThat(report.failureReason).isNull()
            assertThat(report.retryCount).isEqualTo(1)
        }

        @Test
        fun `이미 완료된 보고서는 다시 생성할 수 없다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()
            report.complete(pdfFileKey = PDF_KEY, csvFileKey = CSV_KEY)

            assertThatThrownBy { report.startGenerating() }
                .isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `실패 사유가 너무 길면 컬럼 길이에 맞게 잘린다`() {
            val report = Report.create(EVENT_ID)
            report.startGenerating()

            report.fail("오".repeat(1_000))

            assertThat(report.failureReason).hasSize(500)
        }
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val PDF_KEY = "reports/1/report.pdf"
        private const val CSV_KEY = "reports/1/report.csv"

        private fun snapshot(totalBudget: Long, totalSpent: Long): ReportSnapshot {
            val now = LocalDateTime.of(2026, 8, 24, 19, 0)
            return ReportSnapshot(
                event = ReportSnapshot.EventSummary(
                    eventId = EVENT_ID,
                    title = "여름 MT",
                    startAt = now,
                    endAt = now.plusDays(2),
                    groupId = 10L,
                    groupName = "백엔드 스터디",
                ),
                budget = ReportSnapshot.BudgetSummary(
                    initialBudget = totalBudget,
                    additions = emptyList(),
                    totalBudget = totalBudget,
                    totalSpent = totalSpent,
                    remainingBalance = totalBudget - totalSpent,
                ),
                tagTotals = listOf(
                    ReportSnapshot.TagTotal(tag = "MEAL", label = "식비", amount = totalSpent, count = 1),
                ),
                spendings = listOf(
                    ReportSnapshot.SpendingLine(
                        spendingId = 100L,
                        description = "저녁 식사",
                        amount = totalSpent,
                        tag = "MEAL",
                        payerName = "김석주",
                        spentAt = now.plusDays(1),
                        receiptUrl = null,
                    ),
                ),
            )
        }
    }
}
