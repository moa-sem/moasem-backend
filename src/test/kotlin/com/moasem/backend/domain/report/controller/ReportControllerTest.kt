package com.moasem.backend.domain.report.controller

import com.moasem.backend.domain.report.dto.BudgetSummaryResponse
import com.moasem.backend.domain.report.dto.EventSummaryResponse
import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportDownloadResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.domain.report.dto.TagTotalResponse
import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.ReportStatus
import com.moasem.backend.domain.report.service.ReportDownloadService
import com.moasem.backend.domain.report.service.ReportQueryService
import com.moasem.backend.domain.report.service.ReportRetryService
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(controllers = [ReportController::class])
@Import(GlobalExceptionHandler::class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var reportQueryService: ReportQueryService

    @MockkBean
    private lateinit var reportDownloadService: ReportDownloadService

    @MockkBean
    private lateinit var reportRetryService: ReportRetryService

    @Test
    @DisplayName("보고서를 조회하면 공통 응답으로 감싸서 반환한다")
    fun getReport() {
        every { reportQueryService.getReport(EVENT_ID, USER_ID) } returns detailResponse()

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report").header("X-User-Id", USER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.event.title").value("여름 MT"))
            .andExpect(jsonPath("$.data.budget.remainingBalance").value(180000))
            .andExpect(jsonPath("$.data.tagTotals[0].label").value("식비"))
    }

    @Test
    @DisplayName("AI 실패 시 aiStatus로 구분되고 수치는 정상이다")
    fun aiFailed() {
        every { reportQueryService.getReport(EVENT_ID, USER_ID) } returns
            detailResponse(aiStatus = AiAnalysisStatus.FAILED, aiSummary = null)

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report").header("X-User-Id", USER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.aiStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.aiSummary").doesNotExist())
            .andExpect(jsonPath("$.data.budget.totalSpent").value(320000))
    }

    @Test
    @DisplayName("보고서가 없으면 404와 REPORT_NOT_FOUND")
    fun notFound() {
        every { reportQueryService.getReport(EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.REPORT_NOT_FOUND)

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report").header("X-User-Id", USER_ID))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"))
    }

    @Test
    @DisplayName("모임 구성원이 아니면 403")
    fun forbidden() {
        every { reportQueryService.getReport(EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.NOT_GROUP_MEMBER)

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report").header("X-User-Id", USER_ID))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"))
    }

    @Test
    @DisplayName("상태 조회는 폴링용 필드를 반환한다")
    fun getStatus() {
        every { reportQueryService.getStatus(EVENT_ID, USER_ID) } returns
            ReportStatusResponse(
                eventId = EVENT_ID,
                status = ReportStatus.GENERATING,
                aiStatus = AiAnalysisStatus.PENDING,
                downloadable = false,
                retryable = false,
                failureReason = null,
                generatedAt = null,
            )

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report/status").header("X-User-Id", USER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("GENERATING"))
            .andExpect(jsonPath("$.data.downloadable").value(false))
            .andExpect(jsonPath("$.data.retryable").value(false))
    }

    @Test
    @DisplayName("사용자 ID 헤더가 없으면 400")
    fun missingUserHeader() {
        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report"))
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PDF 다운로드 URL을 발급한다")
    fun getPdfDownload() {
        every { reportDownloadService.getPdfDownload(EVENT_ID, USER_ID) } returns
            ReportDownloadResponse(
                downloadUrl = "https://s3.example/reports/1/report.pdf?sig=x",
                fileName = "여름_MT_결산보고서.pdf",
                expiresAt = LocalDateTime.of(2026, 8, 24, 10, 5),
            )

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report/pdf").header("X-User-Id", USER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3.example/reports/1/report.pdf?sig=x"))
            .andExpect(jsonPath("$.data.fileName").value("여름_MT_결산보고서.pdf"))
    }

    @Test
    @DisplayName("생성이 끝나지 않은 보고서는 409")
    fun downloadNotReady() {
        every { reportDownloadService.getCsvDownload(EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.REPORT_NOT_DOWNLOADABLE)

        mockMvc.perform(get("/api/v1/events/$EVENT_ID/report/csv").header("X-User-Id", USER_ID))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REPORT_NOT_DOWNLOADABLE"))
    }

    private fun detailResponse(
        aiStatus: AiAnalysisStatus = AiAnalysisStatus.SUCCEEDED,
        aiSummary: String? = "예산의 64%를 사용했습니다.",
    ): ReportDetailResponse {
        val now = LocalDateTime.of(2026, 8, 24, 10, 0)
        return ReportDetailResponse(
            eventId = EVENT_ID,
            status = ReportStatus.COMPLETED,
            aiStatus = aiStatus,
            aiSummary = aiSummary,
            event = EventSummaryResponse(
                title = "여름 MT",
                startAt = now,
                endAt = now.plusDays(2),
                groupName = "백엔드 스터디",
                participantCount = 8,
            ),
            budget = BudgetSummaryResponse(
                initialBudget = 500_000L,
                totalBudget = 500_000L,
                totalSpent = 320_000L,
                remainingBalance = 180_000L,
                additions = emptyList(),
            ),
            tagTotals = listOf(
                TagTotalResponse(tag = "MEAL", label = "식비", amount = 320_000L, count = 1),
            ),
            spendings = emptyList(),
            generatedAt = now.plusDays(3),
        )
    }

    @Test
    @DisplayName("재생성을 요청하면 갱신된 상태를 반환한다")
    fun retryReport() {
        every { reportRetryService.retry(EVENT_ID, USER_ID) } returns ReportStatusResponse(
            eventId = EVENT_ID,
            status = ReportStatus.COMPLETED,
            aiStatus = AiAnalysisStatus.SUCCEEDED,
            downloadable = true,
            retryable = false,
            failureReason = null,
            generatedAt = LocalDateTime.of(2026, 8, 27, 10, 0),
        )

        mockMvc.perform(post("/api/v1/events/$EVENT_ID/report/retry").header("X-User-Id", USER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.downloadable").value(true))
    }

    @Test
    @DisplayName("재시도할 수 없는 상태면 409를 반환한다")
    fun retryReportNotRetryable() {
        every { reportRetryService.retry(EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.REPORT_NOT_RETRYABLE)

        mockMvc.perform(post("/api/v1/events/$EVENT_ID/report/retry").header("X-User-Id", USER_ID))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("REPORT_NOT_RETRYABLE"))
    }

    @Test
    @DisplayName("재생성에 X-User-Id가 없으면 400을 반환한다")
    fun retryReportWithoutUserHeader() {
        mockMvc.perform(post("/api/v1/events/$EVENT_ID/report/retry"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    companion object {
        private const val EVENT_ID = 1L
        private const val USER_ID = 42L
    }
}
