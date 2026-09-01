package com.moasem.backend.domain.report.controller

import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportDownloadResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.domain.report.service.ReportDownloadService
import com.moasem.backend.domain.report.service.ReportQueryService
import com.moasem.backend.domain.report.service.ReportRetryService
import com.moasem.backend.global.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결산 보고서 조회.
 *
 * 보고서는 행사당 한 건이므로 reportId 대신 eventId로 접근한다.
 * API 설명은 [ReportControllerDocs]에 있다.
 *
 * 현재 로그인 사용자는 임시로 헤더에서 받는다. auth 도메인이 완성되면
 * @AuthenticationPrincipal 로 교체한다.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/report")
class ReportController(
    private val reportQueryService: ReportQueryService,
    private val reportDownloadService: ReportDownloadService,
    private val reportRetryService: ReportRetryService,
) : ReportControllerDocs {

    @GetMapping
    override fun getReport(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportDetailResponse> =
        ApiResponse.success(reportQueryService.getReport(eventId, currentUserId))

    @GetMapping("/status")
    override fun getStatus(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportStatusResponse> =
        ApiResponse.success(reportQueryService.getStatus(eventId, currentUserId))

    @GetMapping("/pdf")
    override fun getPdfDownload(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportDownloadResponse> =
        ApiResponse.success(reportDownloadService.getPdfDownload(eventId, currentUserId))

    @GetMapping("/csv")
    override fun getCsvDownload(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportDownloadResponse> =
        ApiResponse.success(reportDownloadService.getCsvDownload(eventId, currentUserId))

    @PostMapping("/retry")
    override fun retryReport(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportStatusResponse> =
        ApiResponse.success(reportRetryService.retry(eventId, currentUserId))

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
