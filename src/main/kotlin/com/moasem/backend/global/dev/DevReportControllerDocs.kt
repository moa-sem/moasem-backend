package com.moasem.backend.global.dev

import jakarta.servlet.http.HttpServletRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse

/**
 * 로컬 개발용 API 문서.
 *
 * 운영에는 올라가지 않는다. 로컬 프로파일에서만 등록된다.
 */
@Tag(name = "Dev (로컬 전용)", description = "로컬 저장소에 저장된 보고서 파일을 내려주는 임시 API")
interface DevReportControllerDocs {

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
