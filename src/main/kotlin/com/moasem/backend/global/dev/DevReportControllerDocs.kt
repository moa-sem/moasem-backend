package com.moasem.backend.global.dev

import com.moasem.backend.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse

/**
 * 로컬 개발용 API 문서.
 *
 * 운영에는 올라가지 않는다. 로컬 프로파일에서만 등록된다.
 */
@Tag(name = "Dev (로컬 전용)", description = "로컬에서 보고서 흐름을 확인하기 위한 임시 API")
interface DevReportControllerDocs {

    @Operation(
        summary = "샘플 보고서 생성",
        description = """
            샘플 결산 원자료를 등록하고 보고서를 실제로 생성한다.

            행사 마감으로 보고서를 만드는 실제 경로는 event 컨트롤러가 없어 아직 호출할 수 없다.
            그동안 조회·다운로드 API를 확인하려면 보고서가 하나 있어야 해서 둔다.

            생성 과정은 실제와 같다. 스냅샷 계산, AI 분석, PDF·CSV 생성, 저장까지 모두 거친다.
            원자료만 샘플이다.

            같은 행사로 다시 호출하면 기존 보고서를 지우고 새로 만든다. 반복해서 눌러도 된다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "생성 성공"),
        SwaggerResponse(responseCode = "409", description = "마감되지 않은 행사 (EVENT_NOT_CLOSED)"),
    )
    fun seedReport(
        @Parameter(description = "샘플을 만들 행사 ID. 아무 값이나 써도 된다", example = "1") eventId: Long,
    ): ApiResponse<DevReportSeedResponse>

    @Operation(
        summary = "로컬 저장소 파일 다운로드",
        description = """
            로컬 저장소에 저장된 보고서 파일을 내려준다.

            운영에서는 S3 presigned URL이 S3에서 직접 파일을 주지만, 로컬에는 S3가 없어
            앱이 대신 서빙한다. 다운로드 API가 내려준 URL이 이 경로를 가리킨다.

            직접 호출할 일은 없다. 브라우저에 URL을 붙여넣기만 하면 된다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "파일 반환"),
        SwaggerResponse(responseCode = "404", description = "해당 key에 저장된 파일 없음"),
    )
    fun downloadFile(request: HttpServletRequest): ResponseEntity<ByteArray>
}
