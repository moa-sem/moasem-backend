package com.moasem.backend.domain.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 보고서 파일 다운로드 정보.
 *
 * 서버가 파일을 중계하지 않고 S3 presigned URL을 내려준다. 앱은 이 URL로 직접 받는다.
 */
@Schema(description = "보고서 다운로드 정보")
data class ReportDownloadResponse(
    @field:Schema(
        description = "다운로드 URL. 만료 시각까지만 유효하다",
        example = "https://moasem-backend-storage.s3.ap-northeast-2.amazonaws.com/reports/1/report.pdf?...",
    )
    val downloadUrl: String,

    @field:Schema(description = "저장 시 사용할 파일명", example = "여름_MT_결산보고서.pdf")
    val fileName: String,

    @field:Schema(description = "URL 만료 시각")
    val expiresAt: LocalDateTime,
)
