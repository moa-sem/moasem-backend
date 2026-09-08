package com.moasem.backend.domain.report.dto

import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportStatus
import com.moasem.backend.domain.report.service.ReportSnapshotCalculator
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleAddition
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 응답 변환.
 *
 * 변환이 DTO 안으로 들어왔으므로 서비스를 거치지 않고 직접 확인한다.
 */
class ReportResponseTest {

    private fun snapshot(
        approvedSpendings: List<com.moasem.backend.domain.report.service.port.ApprovedSpendingData> =
            listOf(sampleSpending()),
    ) = ReportSnapshotCalculator().calculate(
        sampleData(
            budgetAdditions = listOf(sampleAddition()),
            approvedSpendings = approvedSpendings,
        ),
    )

    private fun completedReport() = Report.create(EVENT_ID).apply {
        startGenerating()
        applySnapshot(snapshot())
        completeAiAnalysis("총평")
        complete("reports/1/report.pdf", "reports/1/report.csv")
    }

    @Test
    @DisplayName("상태 응답은 다운로드·재시도 가능 여부를 상태에서 끌어낸다")
    fun statusResponse() {
        val response = ReportStatusResponse.from(completedReport())

        assertThat(response.status).isEqualTo(ReportStatus.COMPLETED)
        assertThat(response.aiStatus).isEqualTo(AiAnalysisStatus.SUCCEEDED)
        assertThat(response.downloadable).isTrue()
        assertThat(response.retryable).isFalse()
    }

    @Test
    @DisplayName("실패한 보고서는 재시도 가능으로 표시된다")
    fun failedStatusIsRetryable() {
        val failed = completedReport().apply { fail("S3 오류") }

        val response = ReportStatusResponse.from(failed)

        assertThat(response.retryable).isTrue()
        assertThat(response.downloadable).isFalse()
        assertThat(response.failureReason).isEqualTo("S3 오류")
    }

    @Test
    @DisplayName("지출 내역에 태그별 집계의 한글 라벨이 붙는다")
    fun spendingLinesCarryLabel() {
        // 라벨은 지출 한 건에 들어 있지 않다. 집계에서 가져와 붙이지 않으면 태그 코드가 그대로 나간다.
        val report = completedReport()
        val response = ReportDetailResponse.from(report, report.snapshot!!)

        assertThat(response.spendings).isNotEmpty()
        assertThat(response.spendings.first().tag).isEqualTo("MEAL")
        assertThat(response.spendings.first().label).isEqualTo("식비")
    }

    @Test
    @DisplayName("집계에 없는 태그는 코드를 그대로 쓴다")
    fun unknownTagFallsBackToCode() {
        val report = completedReport()
        val snapshot = report.snapshot!!
        // 집계와 지출의 태그가 어긋나도 응답을 만들지 못하는 상황이 되면 안 된다.
        val mismatched = snapshot.copy(tagTotals = emptyList())

        val response = ReportDetailResponse.from(report, mismatched)

        assertThat(response.spendings.first().label).isEqualTo("MEAL")
    }

    @Test
    @DisplayName("스냅샷의 금액과 추가 예산이 그대로 옮겨진다")
    fun carriesSnapshotNumbers() {
        val report = completedReport()
        val snapshot = report.snapshot!!

        val response = ReportDetailResponse.from(report, snapshot)

        assertThat(response.budget.initialBudget).isEqualTo(snapshot.budget.initialBudget)
        assertThat(response.budget.totalBudget).isEqualTo(snapshot.budget.totalBudget)
        assertThat(response.budget.remainingBalance).isEqualTo(snapshot.budget.remainingBalance)
        assertThat(response.budget.additions).hasSize(1)
        assertThat(response.budget.additions.first().reason).isEqualTo("숙소 추가 예약")
        assertThat(response.event.groupName).isEqualTo(snapshot.event.groupName)
    }

    @Test
    @DisplayName("증빙은 존재 여부만 나가고 저장소 키는 나가지 않는다")
    fun exposesReceiptPresenceOnly() {
        val withReceipt = snapshot(listOf(sampleSpending(receiptKey = "spendings/1/receipt.jpg")))
        val report = Report.create(EVENT_ID).apply {
            startGenerating()
            applySnapshot(withReceipt)
        }

        val line = ReportDetailResponse.from(report, withReceipt).spendings.first()

        assertThat(line.hasReceipt).isTrue()
        assertThat(SpendingLineResponse::class.members.map { it.name })
            .doesNotContain("receiptKey", "receiptUrl")
    }

    companion object {
        private const val EVENT_ID = 1L
    }
}
