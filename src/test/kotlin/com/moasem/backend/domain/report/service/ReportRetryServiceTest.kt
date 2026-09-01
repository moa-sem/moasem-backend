package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.converter.ReportConverter
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportStatus
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeReportAiClient
import com.moasem.backend.domain.report.service.port.FakeReportFileStorage
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.domain.report.service.port.ReportFileStorage
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration

class ReportRetryServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val membershipProvider = mockk<GroupMembershipProvider>()
    private val snapshotProvider = FakeEventSnapshotProvider()
    private val storage = FlakyFileStorage()

    private val stored = mutableMapOf<Long, Report>()
    private lateinit var generationService: ReportGenerationService
    private lateinit var service: ReportRetryService

    @BeforeEach
    fun setUp() {
        stored.clear()
        storage.reset()
        snapshotProvider.given(sampleData(eventId = EVENT_ID))
        every { membershipProvider.isMember(any(), any()) } returns true

        val saved = slot<Report>()
        every { reportRepository.save(capture(saved)) } answers {
            saved.captured.also { stored[it.eventId] = it }
        }
        every { reportRepository.existsByEventId(any()) } answers { stored.containsKey(firstArg()) }
        every { reportRepository.findByEventId(any()) } answers { stored[firstArg<Long>()] }

        generationService = ReportGenerationService(
            reportRepository = reportRepository,
            eventSnapshotProvider = snapshotProvider,
            snapshotCalculator = ReportSnapshotCalculator(),
            pdfGenerator = ReportPdfGenerator(),
            csvGenerator = ReportCsvGenerator(),
            fileStorage = storage,
            aiClient = FakeReportAiClient(),
        )
        service = ReportRetryService(
            accessGuard = ReportAccessGuard(reportRepository, membershipProvider),
            generationService = generationService,
            converter = ReportConverter(),
        )
    }

    /** 저장에 실패시켜 FAILED 상태의 보고서를 만든다. */
    private fun givenFailedReport(): Report {
        storage.shouldFail = true
        return generationService.generate(EVENT_ID).also { storage.shouldFail = false }
    }

    @Test
    fun `실패한 보고서를 재시도하면 완료된다`() {
        val failed = givenFailedReport()
        assertThat(failed.status).isEqualTo(ReportStatus.FAILED)

        val response = service.retry(EVENT_ID, USER_ID)

        assertThat(response.status).isEqualTo(ReportStatus.COMPLETED)
        assertThat(response.downloadable).isTrue()
        assertThat(response.failureReason).isNull()
    }

    @Test
    @DisplayName("모임 구성원이 아니면 재시도할 수 없고 재생성도 일어나지 않는다")
    fun rejectsNonMember() {
        val failed = givenFailedReport()
        every { membershipProvider.isMember(any(), any()) } returns false

        assertThatThrownBy { service.retry(EVENT_ID, USER_ID) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GROUP_MEMBER)

        // 권한 검증은 생성보다 먼저 끝나야 한다. 재시도 횟수가 늘었다면 생성이 이미 돌았다는 뜻이다.
        assertThat(failed.retryCount).isZero()
        assertThat(failed.status).isEqualTo(ReportStatus.FAILED)
    }

    @Test
    fun `보고서가 없으면 REPORT_NOT_FOUND`() {
        assertThatThrownBy { service.retry(999L, USER_ID) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_FOUND)
    }

    @Test
    fun `완료된 보고서는 재시도할 수 없다`() {
        generationService.generate(EVENT_ID)

        assertThatThrownBy { service.retry(EVENT_ID, USER_ID) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_NOT_RETRYABLE)
    }

    /** 첫 시도만 실패시키고 이후에는 정상 동작하는 저장소. */
    private class FlakyFileStorage : ReportFileStorage {
        private val delegate = FakeReportFileStorage()
        var shouldFail = false

        fun reset() {
            shouldFail = false
        }

        override fun upload(key: String, content: ByteArray, contentType: String): String {
            if (shouldFail) throw RuntimeException("저장소 일시 오류")
            return delegate.upload(key, content, contentType)
        }

        override fun generateDownloadUrl(key: String, expiry: Duration) =
            delegate.generateDownloadUrl(key, expiry)
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val USER_ID = 42L
    }
}
