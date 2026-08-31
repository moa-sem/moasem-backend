package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.converter.ReportConverter
import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportStatus
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ReportQueryServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val membershipProvider = mockk<GroupMembershipProvider>()
    private val service = ReportQueryService(reportRepository, membershipProvider, ReportConverter())

    @BeforeEach
    fun setUp() {
        every { membershipProvider.isMember(any(), any()) } returns true
    }

    /** 스냅샷까지 확정된 완료 상태의 보고서를 만든다. */
    private fun completedReport(
        eventId: Long = EVENT_ID,
        aiSummary: String? = "예산의 64%를 사용했습니다.",
    ): Report {
        val snapshot = ReportSnapshotCalculator().calculate(
            sampleData(
                eventId = eventId,
                initialBudget = 500_000L,
                approvedSpendings = listOf(sampleSpending(amount = 320_000L)),
            ),
        )
        return Report.create(eventId).apply {
            startGenerating()
            applySnapshot(snapshot)
            if (aiSummary != null) completeAiAnalysis(aiSummary) else failAiAnalysis()
            complete("reports/$eventId/report.pdf", "reports/$eventId/report.csv")
        }
    }

    @Nested
    @DisplayName("보고서 조회")
    inner class GetReport {

        @Test
        fun `스냅샷 내용이 그대로 반환된다`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            val response = service.getReport(EVENT_ID, USER_ID)

            assertThat(response.event.title).isEqualTo("여름 MT")
            assertThat(response.budget.totalBudget).isEqualTo(500_000L)
            assertThat(response.budget.totalSpent).isEqualTo(320_000L)
            assertThat(response.budget.remainingBalance).isEqualTo(180_000L)
            assertThat(response.tagTotals).isNotEmpty()
            assertThat(response.spendings).hasSize(1)
        }

        @Test
        @DisplayName("지출 내역에도 한글 라벨이 붙는다")
        fun spendingsCarryLabel() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            val response = service.getReport(EVENT_ID, USER_ID)

            assertThat(response.spendings.first().label).isEqualTo("식비")
        }

        @Test
        @DisplayName("AI가 실패해도 조회되고 aiStatus로 구분된다")
        fun aiFailedReportIsStillReadable() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport(aiSummary = null)

            val response = service.getReport(EVENT_ID, USER_ID)

            assertThat(response.aiStatus).isEqualTo(AiAnalysisStatus.FAILED)
            assertThat(response.aiSummary).isNull()
            // 수치는 정상이어야 한다.
            assertThat(response.budget.totalSpent).isEqualTo(320_000L)
        }

        @Test
        fun `보고서가 없으면 REPORT_NOT_FOUND`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns null

            assertThatThrownBy { service.getReport(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("상태 조회")
    inner class GetStatus {

        @Test
        fun `완료된 보고서는 다운로드 가능으로 나온다`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            val response = service.getStatus(EVENT_ID, USER_ID)

            assertThat(response.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(response.downloadable).isTrue()
            assertThat(response.retryable).isFalse()
            assertThat(response.failureReason).isNull()
        }

        @Test
        @DisplayName("생성 중인 보고서도 상태 조회는 된다")
        fun generatingReportIsQueryable() {
            val report = completedReport().apply { fail("PDF 생성 실패") }
            every { reportRepository.findByEventId(EVENT_ID) } returns report

            val response = service.getStatus(EVENT_ID, USER_ID)

            assertThat(response.status).isEqualTo(ReportStatus.FAILED)
            assertThat(response.retryable).isTrue()
            assertThat(response.failureReason).isEqualTo("PDF 생성 실패")
        }
    }

    @Nested
    @DisplayName("권한")
    inner class Access {

        @Test
        fun `모임 구성원이 아니면 거부된다`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()
            every { membershipProvider.isMember(any(), any()) } returns false

            assertThatThrownBy { service.getReport(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GROUP_MEMBER)
        }

        @Test
        @DisplayName("상태 조회에도 같은 권한 검증이 걸린다")
        fun statusRequiresMembershipToo() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()
            every { membershipProvider.isMember(any(), any()) } returns false

            assertThatThrownBy { service.getStatus(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GROUP_MEMBER)
        }

        @Test
        @DisplayName("권한 검증은 스냅샷의 groupId로 한다")
        fun usesGroupIdFromSnapshot() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            service.getReport(EVENT_ID, USER_ID)

            io.mockk.verify { membershipProvider.isMember(10L, USER_ID) }
        }
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val USER_ID = 42L
    }
}
