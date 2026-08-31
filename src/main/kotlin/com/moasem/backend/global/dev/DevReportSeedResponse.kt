package com.moasem.backend.global.dev

import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 샘플 보고서 생성 결과.
 *
 * 다음에 호출할 경로를 함께 내려준다. 로컬에서 흐름을 따라가 보는 게 목적이라
 * Swagger 화면과 코드를 오가지 않아도 되게 한다.
 */
@Schema(description = "샘플 보고서 생성 결과 (로컬 전용)")
data class DevReportSeedResponse(
    @field:Schema(description = "행사 ID", example = "1")
    val eventId: Long,

    @field:Schema(description = "보고서 생성 상태", example = "COMPLETED")
    val status: ReportStatus,

    @field:Schema(description = "AI 분석 상태", example = "SUCCEEDED")
    val aiStatus: AiAnalysisStatus,

    @field:Schema(description = "실패 사유. 성공 시 null")
    val failureReason: String?,

    @field:Schema(description = "이 헤더 값으로 조회·다운로드 API를 호출하면 된다", example = "42")
    val useUserIdHeader: Long,

    @field:Schema(description = "다음에 호출해 볼 경로")
    val nextSteps: List<String>,
) {
    companion object {
        fun from(report: Report, userId: Long) = DevReportSeedResponse(
            eventId = report.eventId,
            status = report.status,
            aiStatus = report.aiStatus,
            failureReason = report.failureReason,
            useUserIdHeader = userId,
            nextSteps = listOf(
                "GET /api/v1/events/${report.eventId}/report",
                "GET /api/v1/events/${report.eventId}/report/status",
                "GET /api/v1/events/${report.eventId}/report/pdf",
                "GET /api/v1/events/${report.eventId}/report/csv",
            ),
        )
    }
}
