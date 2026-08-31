package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeReportFileStorage
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
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

class ReportDownloadServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val membershipProvider = mockk<GroupMembershipProvider>()
    private val fileStorage = FakeReportFileStorage()
    private val service = ReportDownloadService(reportRepository, membershipProvider, fileStorage)

    @BeforeEach
    fun setUp() {
        every { membershipProvider.isMember(any(), any()) } returns true
        // 파일이 저장돼 있어야 URL 발급이 가능하다.
        fileStorage.upload(PDF_KEY, "pdf".toByteArray(), "application/pdf")
        fileStorage.upload(CSV_KEY, "csv".toByteArray(), "text/csv")
    }

    private fun completedReport(title: String = "여름 MT"): Report {
        val snapshot = ReportSnapshotCalculator().calculate(sampleData(eventId = EVENT_ID))
            .let { it.copy(event = it.event.copy(title = title)) }
        return Report.create(EVENT_ID).apply {
            startGenerating()
            applySnapshot(snapshot)
            completeAiAnalysis("분석")
            complete(PDF_KEY, CSV_KEY)
        }
    }

    @Nested
    @DisplayName("URL 발급")
    inner class Issue {

        @Test
        fun `완료된 보고서의 PDF URL을 발급한다`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            val response = service.getPdfDownload(EVENT_ID, USER_ID)

            assertThat(response.downloadUrl).contains(PDF_KEY)
            assertThat(response.fileName).isEqualTo("여름_MT_결산보고서.pdf")
            assertThat(response.expiresAt).isAfter(java.time.LocalDateTime.now())
        }

        @Test
        fun `완료된 보고서의 CSV URL을 발급한다`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()

            val response = service.getCsvDownload(EVENT_ID, USER_ID)

            assertThat(response.downloadUrl).contains(CSV_KEY)
            assertThat(response.fileName).isEqualTo("여름_MT_결산보고서.csv")
        }

        @Test
        @DisplayName("행사명에 파일명으로 쓸 수 없는 문자가 있으면 걸러낸다")
        fun sanitizesFileName() {
            every { reportRepository.findByEventId(EVENT_ID) } returns
                completedReport(title = "여름/MT: 제주 \"여행\"")

            val response = service.getPdfDownload(EVENT_ID, USER_ID)

            assertThat(response.fileName).doesNotContain("/", ":", "\"")
            assertThat(response.fileName).endsWith("_결산보고서.pdf")
        }
    }

    @Nested
    @DisplayName("접근 제한")
    inner class Access {

        @Test
        @DisplayName("모임 구성원이 아니면 URL을 발급하지 않는다")
        fun rejectsNonMember() {
            every { reportRepository.findByEventId(EVENT_ID) } returns completedReport()
            every { membershipProvider.isMember(any(), any()) } returns false

            // presigned URL은 발급되면 링크만으로 접근 가능하다. 발급 전에 막아야 한다.
            assertThatThrownBy { service.getPdfDownload(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GROUP_MEMBER)
        }

        @Test
        fun `생성 중인 보고서는 다운로드할 수 없다`() {
            val generating = Report.create(EVENT_ID).apply {
                startGenerating()
                applySnapshot(ReportSnapshotCalculator().calculate(sampleData(eventId = EVENT_ID)))
            }
            every { reportRepository.findByEventId(EVENT_ID) } returns generating

            assertThatThrownBy { service.getPdfDownload(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_DOWNLOADABLE)
        }

        @Test
        fun `실패한 보고서는 다운로드할 수 없다`() {
            val failed = completedReport().apply { fail("PDF 생성 실패") }
            every { reportRepository.findByEventId(EVENT_ID) } returns failed

            assertThatThrownBy { service.getCsvDownload(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_DOWNLOADABLE)
        }

        @Test
        fun `보고서가 없으면 REPORT_NOT_FOUND`() {
            every { reportRepository.findByEventId(EVENT_ID) } returns null

            assertThatThrownBy { service.getPdfDownload(EVENT_ID, USER_ID) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND)
        }
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val USER_ID = 42L
        private const val PDF_KEY = "reports/1/report.pdf"
        private const val CSV_KEY = "reports/1/report.csv"
    }
}
