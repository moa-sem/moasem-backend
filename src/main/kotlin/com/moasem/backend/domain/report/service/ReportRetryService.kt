package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.converter.ReportConverter
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 생성에 실패한 보고서를 다시 만든다.
 *
 * 최초 생성은 행사 마감이 트리거라 사용자 컨텍스트가 없지만, 재시도는 사용자가 버튼을
 * 눌러 호출하는 API다. 그래서 권한 검증이 여기 붙고 [ReportGenerationService]에는 없다.
 * 생성 오케스트레이션 자체는 두 경로가 완전히 같으므로 그대로 위임한다.
 */
@Service
class ReportRetryService(
    private val accessGuard: ReportAccessGuard,
    private val generationService: ReportGenerationService,
    private val converter: ReportConverter,
) {

    /**
     * 재시도 가능 여부는 [ReportGenerationService.retry]가 판단한다.
     *
     * 여기서 미리 걸러내면 판단 기준이 두 곳으로 갈라진다. 이 서비스는 "누가 호출했는가"만
     * 책임지고, "재시도해도 되는 상태인가"는 생성 쪽에 맡긴다.
     *
     * @throws com.moasem.backend.global.error.BusinessException
     *   보고서가 없거나(REPORT_NOT_FOUND), 모임 구성원이 아니거나(NOT_GROUP_MEMBER),
     *   재시도할 수 없는 상태인 경우(REPORT_NOT_RETRYABLE)
     */
    @Transactional
    fun retry(eventId: Long, currentUserId: Long): ReportStatusResponse {
        accessGuard.findAccessibleReport(eventId, currentUserId)

        val report = generationService.retry(eventId)
        return converter.toStatusResponse(report)
    }
}
