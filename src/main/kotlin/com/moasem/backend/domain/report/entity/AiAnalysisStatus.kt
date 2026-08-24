package com.moasem.backend.domain.report.entity

/**
 * AI 결산 분석 상태.
 *
 * AI 실패는 보고서 생성 실패가 아니다. 실패해도 기본 PDF·CSV는 제공해야 하므로
 * [ReportStatus]와 분리해 관리한다.
 */
enum class AiAnalysisStatus {
    /** 아직 분석을 시도하지 않은 상태. */
    PENDING,

    /** 분석에 성공해 요약문이 저장된 상태. */
    SUCCEEDED,

    /** 분석에 실패한 상태. 보고서 자체는 정상 제공된다. */
    FAILED,
}
