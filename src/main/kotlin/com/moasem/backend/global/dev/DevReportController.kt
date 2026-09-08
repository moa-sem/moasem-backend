package com.moasem.backend.global.dev

import com.moasem.backend.domain.report.service.adapter.LocalReportFileStorage
import com.moasem.backend.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬 저장소에 저장된 보고서 파일을 서빙하는 임시 컨트롤러.
 *
 * 로컬에는 S3가 없어 다운로드 URL이 가리킬 곳이 없다. 그 자리만 대신한다.
 * 샘플 보고서를 만들어 주던 엔드포인트는 실제 마감 데이터로 보고서가 만들어지게 되면서 지웠다.
 *
 * API 설명은 [DevReportControllerDocs]에 있다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
class DevReportController(
    private val fileStorage: LocalReportFileStorage,
) : DevReportControllerDocs {

    /**
     * 저장된 파일을 그대로 내려준다.
     *
     * key에 `/`가 들어 있어(`reports/1/report.pdf`) `@PathVariable`로는 받을 수 없다.
     * 와일드카드 경로로 받고, 요청 경로에서 접두사를 잘라 낸다.
     */
    @GetMapping("/files/**")
    override fun downloadFile(request: HttpServletRequest): ResponseEntity<ByteArray> {
        val key = request.requestURI.substringAfter(LocalReportFileStorage.DOWNLOAD_PATH_PREFIX)
        val content = fileStorage.read(key) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentTypeOf(key))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${key.substringAfterLast('/')}\"")
            .body(content)
    }

    private fun contentTypeOf(key: String) = when (key.substringAfterLast('.')) {
        "pdf" -> "application/pdf"
        "csv" -> "text/csv; charset=UTF-8"
        else -> "application/octet-stream"
    }
}
