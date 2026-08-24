package com.moasem.backend.domain.report.entity

/**
 * 보고서 생성 진행 상태.
 *
 * AI 분석 성공 여부와는 독립적이다. AI가 실패해도 PDF·CSV가 만들어졌다면 [COMPLETED]다.
 * AI 분석 상태는 [AiAnalysisStatus]로 따로 관리한다.
 */
enum class ReportStatus {
    /** 행사 마감으로 보고서 행만 생성된 상태. 아직 아무것도 계산하지 않았다. */
    PENDING,

    /** 결산 스냅샷 계산 또는 파일 생성이 진행 중인 상태. */
    GENERATING,

    /** PDF·CSV까지 생성이 끝나 조회·다운로드가 가능한 상태. */
    COMPLETED,

    /** 파일 생성에 실패한 상태. 재시도 대상이다. */
    FAILED,
    ;

    val isRetryable: Boolean
        get() = this == FAILED
}
