package com.moasem.backend.domain.report.controller

import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.domain.report.service.ReportQueryService
import com.moasem.backend.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결산 보고서 조회.
 *
 * 보고서는 행사당 한 건이므로 reportId 대신 eventId로 접근한다.
 *
 * 현재 로그인 사용자는 임시로 헤더에서 받는다. auth 도메인이 완성되면
 * @AuthenticationPrincipal 로 교체한다.
 */
@Tag(name = "Report", description = "결산 보고서")
@RestController
@RequestMapping("/api/v1/events/{eventId}/report")
class ReportController(
    private val reportQueryService: ReportQueryService,
) {

    @Operation(
        summary = "결산 보고서 조회",
        description = "확정된 결산 내용을 반환한다. 모든 수치는 생성 시점에 확정된 값이라 조회 시점과 무관하게 동일하다. " +
            "aiStatus가 FAILED면 aiSummary는 null이며, 나머지 수치는 정상이다.",
    )
    @GetMapping
    fun getReport(
        @PathVariable eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID (임시)") @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportDetailResponse> =
        ApiResponse.success(reportQueryService.getReport(eventId, currentUserId))

    @Operation(
        summary = "보고서 생성 상태 조회",
        description = "행사 마감 직후 생성이 끝날 때까지 폴링하는 용도다. 생성 중인 보고서도 조회할 수 있다.",
    )
    @GetMapping("/status")
    fun getStatus(
        @PathVariable eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID (임시)") @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<ReportStatusResponse> =
        ApiResponse.success(reportQueryService.getStatus(eventId, currentUserId))

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
