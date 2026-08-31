package com.moasem.backend.domain.report.controller

import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportDownloadResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse

/**
 * 결산 보고서 API 문서.
 *
 * Swagger 어노테이션을 여기에 모아 컨트롤러에는 매핑과 위임만 남긴다.
 * springdoc은 구현 클래스뿐 아니라 인터페이스에 선언된 어노테이션도 읽는다.
 *
 * 공통 응답 래퍼와 이름이 겹쳐 Swagger 쪽 응답 어노테이션은 SwaggerResponse로 별칭을 준다.
 */
@Tag(name = "Report", description = "결산 보고서")
interface ReportControllerDocs {

    @Operation(
        summary = "결산 보고서 조회",
        description = """
            확정된 결산 내용을 반환한다.

            모든 수치는 보고서 생성 시점에 확정된 스냅샷에서 나온다. 이후 원본 지출이 바뀌어도
            보고서 값은 변하지 않으므로, 몇 번을 조회하든 같은 결과가 나온다.

            aiStatus가 FAILED면 aiSummary는 없다. 이때도 결산 수치는 정상이므로
            AI 분석 영역만 비우고 나머지는 그대로 표시하면 된다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(
            responseCode = "404",
            description = "보고서 없음 (REPORT_NOT_FOUND). 행사가 아직 마감되지 않은 경우도 포함된다",
        ),
        SwaggerResponse(responseCode = "409", description = "생성이 끝나지 않음 (REPORT_GENERATING)"),
    )
    fun getReport(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID. auth 완성 전까지 임시로 헤더로 받는다", example = "42")
        currentUserId: Long,
    ): ApiResponse<ReportDetailResponse>

    @Operation(
        summary = "보고서 생성 상태 조회",
        description = """
            행사 마감 직후 생성이 끝날 때까지 폴링하는 용도다.

            생성에는 스냅샷 계산, AI 분석, PDF·CSV 생성이 포함되어 수 초에서 수십 초가 걸린다.
            생성 중인 보고서도 조회할 수 있다.

            상태별로 프론트가 할 일
            - GENERATING : 생성 중 안내
            - COMPLETED  : downloadable이 true면 다운로드 버튼 활성화
            - FAILED     : retryable이 true면 재시도 버튼 노출, failureReason 표시
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "보고서 없음 (REPORT_NOT_FOUND)"),
    )
    fun getStatus(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID. auth 완성 전까지 임시로 헤더로 받는다", example = "42")
        currentUserId: Long,
    ): ApiResponse<ReportStatusResponse>

    @Operation(
        summary = "PDF 다운로드 URL 발급",
        description = """
            보고서 PDF를 내려받을 수 있는 URL을 발급한다.

            서버가 파일을 중계하지 않고 S3 presigned URL을 내려준다. 앱은 이 URL로 직접 받는다.
            발급된 URL은 그 자체가 통행증이라 만료 전까지는 링크를 아는 누구나 내려받을 수 있으므로,
            앱은 받은 즉시 사용하고 저장하거나 공유하지 않는다.

            생성이 완료된 보고서만 발급된다. 진행 상태는 상태 조회 API로 먼저 확인한다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "발급 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "보고서 없음 (REPORT_NOT_FOUND)"),
        SwaggerResponse(
            responseCode = "409",
            description = "아직 다운로드할 수 없음 (REPORT_NOT_DOWNLOADABLE). 생성 중이거나 실패한 상태다",
        ),
    )
    fun getPdfDownload(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID. auth 완성 전까지 임시로 헤더로 받는다", example = "42")
        currentUserId: Long,
    ): ApiResponse<ReportDownloadResponse>

    @Operation(
        summary = "CSV 다운로드 URL 발급",
        description = """
            보고서 CSV를 내려받을 수 있는 URL을 발급한다. 발급 방식은 PDF와 같다.

            CSV는 엑셀에서 다시 가공하는 용도라 요약과 지출 내역만 담기며, AI 분석은 포함되지 않는다.
            따라서 AI 분석이 실패한 보고서도 CSV는 온전하다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "발급 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "보고서 없음 (REPORT_NOT_FOUND)"),
        SwaggerResponse(
            responseCode = "409",
            description = "아직 다운로드할 수 없음 (REPORT_NOT_DOWNLOADABLE)",
        ),
    )
    fun getCsvDownload(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID. auth 완성 전까지 임시로 헤더로 받는다", example = "42")
        currentUserId: Long,
    ): ApiResponse<ReportDownloadResponse>
}
