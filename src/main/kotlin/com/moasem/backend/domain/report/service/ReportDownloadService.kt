package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.dto.ReportDownloadResponse
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.domain.report.service.port.ReportFileStorage
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * 보고서 파일 다운로드 URL 발급.
 *
 * 발급된 presigned URL은 그 자체가 통행증이라 링크를 아는 누구나 유효 기간 동안 내려받을 수
 * 있다. 그래서 발급 전에 반드시 권한과 상태를 검증하고, 유효 기간을 짧게 유지한다.
 */
@Service
@Transactional(readOnly = true)
class ReportDownloadService(
    private val reportRepository: ReportRepository,
    private val groupMembershipProvider: GroupMembershipProvider,
    private val fileStorage: ReportFileStorage,
) {

    fun getPdfDownload(eventId: Long, currentUserId: Long): ReportDownloadResponse {
        val report = findDownloadableReport(eventId, currentUserId)
        return toResponse(report, requireKey(report.pdfFileKey), PDF_EXTENSION)
    }

    fun getCsvDownload(eventId: Long, currentUserId: Long): ReportDownloadResponse {
        val report = findDownloadableReport(eventId, currentUserId)
        return toResponse(report, requireKey(report.csvFileKey), CSV_EXTENSION)
    }

    /**
     * 보고서를 찾고 다운로드 가능한 상태인지 확인한다.
     *
     * 조회 API와 같은 권한 검증에 더해 완료 여부까지 본다. 생성 중이거나 실패한 보고서는
     * 파일 자체가 없거나 불완전하다.
     */
    private fun findDownloadableReport(eventId: Long, currentUserId: Long): Report {
        val report = reportRepository.findByEventId(eventId)
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)

        val groupId = report.snapshot?.event?.groupId
            ?: throw BusinessException(ErrorCode.REPORT_GENERATING)

        if (!groupMembershipProvider.isMember(groupId, currentUserId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        }

        if (!report.isDownloadable) {
            throw BusinessException(ErrorCode.REPORT_NOT_DOWNLOADABLE)
        }

        return report
    }

    private fun requireKey(key: String?): String =
        key ?: throw BusinessException(ErrorCode.REPORT_NOT_DOWNLOADABLE)

    private fun toResponse(report: Report, key: String, extension: String) = ReportDownloadResponse(
        downloadUrl = fileStorage.generateDownloadUrl(key, URL_EXPIRY),
        fileName = buildFileName(report, extension),
        expiresAt = LocalDateTime.now().plus(URL_EXPIRY),
    )

    /**
     * 저장 시 보일 파일명.
     *
     * 행사명에 파일명으로 쓸 수 없는 문자가 들어갈 수 있어 걸러낸다. 걸러낸 결과가 비면
     * 기본 이름을 쓴다.
     */
    private fun buildFileName(report: Report, extension: String): String {
        val title = report.snapshot?.event?.title
            ?.replace(FILE_NAME_UNSAFE, "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_FILE_NAME

        return "${title}_결산보고서.$extension"
    }

    companion object {
        /** 앱이 받자마자 내려받으므로 짧게 유지한다. 길면 URL 공유만으로 누구나 받을 수 있다. */
        private val URL_EXPIRY: Duration = Duration.ofMinutes(5)

        private const val PDF_EXTENSION = "pdf"
        private const val CSV_EXTENSION = "csv"
        private const val DEFAULT_FILE_NAME = "행사"
        private val FILE_NAME_UNSAFE = Regex("""[\\/:*?"<>|\s]+""")
    }
}
