package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportStatus
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import com.moasem.backend.domain.report.service.port.FakeReportAiClient
import com.moasem.backend.domain.report.service.port.FakeReportFileStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ReportGenerationServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val snapshotProvider = FakeEventSnapshotProvider()
    private val fileStorage = FakeReportFileStorage()
    private val aiClient = FakeReportAiClient()

    private lateinit var service: ReportGenerationService
    private val stored = mutableMapOf<Long, Report>()

    @BeforeEach
    fun setUp() {
        stored.clear()
        snapshotProvider.given(sampleData(eventId = EVENT_ID))

        val saved = slot<Report>()
        every { reportRepository.save(capture(saved)) } answers {
            saved.captured.also { stored[it.eventId] = it }
        }
        every { reportRepository.existsByEventId(any()) } answers { stored.containsKey(firstArg()) }
        every { reportRepository.findByEventId(any()) } answers { stored[firstArg<Long>()] }

        service = ReportGenerationService(
            reportRepository = reportRepository,
            eventSnapshotProvider = snapshotProvider,
            snapshotCalculator = ReportSnapshotCalculator(),
            pdfGenerator = ReportPdfGenerator(),
            csvGenerator = ReportCsvGenerator(),
            fileStorage = fileStorage,
            aiClient = aiClient,
        )
    }

    @Nested
    @DisplayName("정상 생성")
    inner class Success {

        @Test
        fun `완료 상태가 되고 파일 키가 저장된다`() {
            val report = service.generate(EVENT_ID)

            assertThat(report.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(report.pdfFileKey).isEqualTo("reports/$EVENT_ID/report.pdf")
            assertThat(report.csvFileKey).isEqualTo("reports/$EVENT_ID/report.csv")
            assertThat(report.isDownloadable).isTrue()
            assertThat(report.generatedAt).isNotNull()
        }

        @Test
        fun `PDF와 CSV가 모두 저장소에 올라간다`() {
            service.generate(EVENT_ID)

            assertThat(fileStorage.storedKeys()).containsExactlyInAnyOrder(
                "reports/$EVENT_ID/report.pdf",
                "reports/$EVENT_ID/report.csv",
            )
            assertThat(fileStorage.read("reports/$EVENT_ID/report.pdf")!!.contentType)
                .isEqualTo("application/pdf")
        }

        @Test
        fun `스냅샷이 확정되고 조회용 금액이 채워진다`() {
            snapshotProvider.given(
                sampleData(
                    eventId = EVENT_ID,
                    initialBudget = 500_000L,
                    approvedSpendings = listOf(sampleSpending(amount = 320_000L)),
                ),
            )

            val report = service.generate(EVENT_ID)

            assertThat(report.snapshot).isNotNull()
            assertThat(report.totalBudget).isEqualTo(500_000L)
            assertThat(report.remainingBalance).isEqualTo(180_000L)
        }

        @Test
        fun `AI 분석 결과가 저장된다`() {
            aiClient.respondWith("예산의 64%를 사용했습니다.")

            val report = service.generate(EVENT_ID)

            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.SUCCEEDED)
            assertThat(report.aiSummary).isEqualTo("예산의 64%를 사용했습니다.")
        }

        @Test
        @DisplayName("AI에는 집계값만 전달된다")
        fun aiReceivesOnlyAggregates() {
            service.generate(EVENT_ID)

            val input = aiClient.lastInput!!
            assertThat(input.eventTitle).isEqualTo("여름 MT")
            assertThat(input.tagTotals).isNotEmpty()
        }
    }

    @Nested
    @DisplayName("AI 실패")
    inner class AiFailure {

        @Test
        @DisplayName("AI가 실패해도 보고서는 완료된다")
        fun completesDespiteAiFailure() {
            aiClient.failWith("타임아웃")

            val report = service.generate(EVENT_ID)

            assertThat(report.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(report.aiStatus).isEqualTo(AiAnalysisStatus.FAILED)
            assertThat(report.aiSummary).isNull()
        }

        @Test
        fun `AI가 실패해도 PDF와 CSV는 모두 저장된다`() {
            aiClient.failWith()

            service.generate(EVENT_ID)

            assertThat(fileStorage.storedKeys()).hasSize(2)
        }
    }

    @Nested
    @DisplayName("실패와 재시도")
    inner class Retry {

        @Test
        fun `파일 저장에 실패하면 보고서가 실패 상태가 된다`() {
            val failingStorage = mockk<com.moasem.backend.domain.report.service.port.ReportFileStorage>()
            every { failingStorage.upload(any(), any(), any()) } throws RuntimeException("S3 연결 실패")
            service = serviceWith(storage = failingStorage)

            val report = service.generate(EVENT_ID)

            assertThat(report.status).isEqualTo(ReportStatus.FAILED)
            assertThat(report.failureReason).contains("S3 연결 실패")
        }

        @Test
        @DisplayName("재시도해도 스냅샷은 다시 계산하지 않는다")
        fun reusesSnapshotOnRetry() {
            val failingStorage = mockk<com.moasem.backend.domain.report.service.port.ReportFileStorage>()
            every { failingStorage.upload(any(), any(), any()) } throws RuntimeException("일시적 오류")
            service = serviceWith(storage = failingStorage)
            val failed = service.generate(EVENT_ID)
            val snapshotAfterFailure = failed.snapshot

            // 재시도 전에 원본 데이터가 바뀌어도 스냅샷은 그대로여야 한다.
            snapshotProvider.given(
                sampleData(eventId = EVENT_ID, initialBudget = 999_999L),
            )
            service = serviceWith(storage = fileStorage)

            val retried = service.retry(EVENT_ID)

            assertThat(retried.status).isEqualTo(ReportStatus.COMPLETED)
            assertThat(retried.snapshot).isEqualTo(snapshotAfterFailure)
            assertThat(retried.totalBudget).isNotEqualTo(999_999L)
            assertThat(retried.retryCount).isEqualTo(1)
        }

        @Test
        fun `실패하지 않은 보고서는 재시도할 수 없다`() {
            service.generate(EVENT_ID)

            assertThatThrownBy { service.retry(EVENT_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("재시도할 수 없는 상태")
        }

        @Test
        fun `없는 보고서는 재시도할 수 없다`() {
            assertThatThrownBy { service.retry(999L) }
                .isInstanceOf(NoSuchElementException::class.java)
        }
    }

    @Nested
    @DisplayName("사전 조건")
    inner class Preconditions {

        @Test
        fun `행사당 보고서는 한 건만 만들 수 있다`() {
            service.generate(EVENT_ID)

            assertThatThrownBy { service.generate(EVENT_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("이미 보고서가 존재")
        }

        @Test
        @DisplayName("마감되지 않은 행사는 보고서 행을 만들기 전에 거부된다")
        fun rejectsActiveEventBeforeCreatingRow() {
            snapshotProvider.given(sampleData(eventId = EVENT_ID, status = "ACTIVE"))

            assertThatThrownBy { service.generate(EVENT_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("마감된 행사만")

            // FAILED 행이 남으면 나중에 행사가 실제로 마감돼도 event_id unique 제약 때문에
            // 새 보고서를 만들 수 없다. 행 자체가 생기지 않아야 한다.
            assertThat(stored).isEmpty()
        }

        @Test
        @DisplayName("거부된 뒤 행사가 마감되면 정상 생성된다")
        fun generatesAfterEventIsClosed() {
            snapshotProvider.given(sampleData(eventId = EVENT_ID, status = "ACTIVE"))
            assertThatThrownBy { service.generate(EVENT_ID) }
                .isInstanceOf(IllegalStateException::class.java)

            snapshotProvider.given(sampleData(eventId = EVENT_ID, status = "CLOSED"))

            val report = service.generate(EVENT_ID)

            assertThat(report.status).isEqualTo(ReportStatus.COMPLETED)
        }
    }

    private fun serviceWith(
        storage: com.moasem.backend.domain.report.service.port.ReportFileStorage,
    ) = ReportGenerationService(
        reportRepository = reportRepository,
        eventSnapshotProvider = snapshotProvider,
        snapshotCalculator = ReportSnapshotCalculator(),
        pdfGenerator = ReportPdfGenerator(),
        csvGenerator = ReportCsvGenerator(),
        fileStorage = storage,
        aiClient = aiClient,
    )

    companion object {
        private const val EVENT_ID = 1L
    }
}
