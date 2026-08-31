package com.moasem.backend.global.dev

import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.ReportGenerationService
import com.moasem.backend.domain.report.service.adapter.LocalReportFileStorage
import com.moasem.backend.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬에서 보고서 흐름을 직접 확인하기 위한 임시 컨트롤러.
 *
 * 두 가지가 없어서 둔다.
 * - 보고서를 만드는 실제 트리거(행사 마감)를 호출할 event 컨트롤러가 아직 없다.
 * - 로컬에는 S3가 없어 다운로드 URL이 가리킬 곳이 없다.
 *
 * 둘 다 채워지면 이 컨트롤러는 지운다. API 설명은 [DevReportControllerDocs]에 있다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
class DevReportController(
    private val snapshotStore: DevEventSnapshotStore,
    private val reportGenerationService: ReportGenerationService,
    private val reportRepository: ReportRepository,
    private val fileStorage: LocalReportFileStorage,
) : DevReportControllerDocs {

    /**
     * 기존 보고서를 지우고 다시 만든다.
     *
     * 행사당 보고서는 하나뿐이라 두 번째 호출부터는 REPORT_ALREADY_EXISTS로 막힌다.
     * 스웨거에서 반복해서 눌러 보는 게 이 API의 용도라 매번 새로 만든다.
     */
    @PostMapping("/reports/{eventId}")
    @Transactional
    override fun seedReport(@PathVariable eventId: Long): ApiResponse<DevReportSeedResponse> {
        reportRepository.findByEventId(eventId)?.let(reportRepository::delete)
        reportRepository.flush()

        snapshotStore.seed(eventId)
        val report = reportGenerationService.generate(eventId)

        return ApiResponse.success(DevReportSeedResponse.from(report, SAMPLE_USER_ID))
    }

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

    companion object {
        /** 로컬 스텁이 모든 사용자를 구성원으로 보므로 어떤 값이든 통과한다. */
        private const val SAMPLE_USER_ID = 42L
    }
}
