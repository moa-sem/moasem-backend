package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.converter.ReportConverter
import com.moasem.backend.domain.report.dto.ReportDetailResponse
import com.moasem.backend.domain.report.dto.ReportStatusResponse
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결산 보고서 조회.
 *
 * 모든 수치는 확정된 스냅샷에서 나오므로 event·spending 도메인을 거치지 않는다.
 * 조회 시점이 언제든 같은 값이 나온다.
 */
@Service
@Transactional(readOnly = true)
class ReportQueryService(
    private val reportRepository: ReportRepository,
    private val groupMembershipProvider: GroupMembershipProvider,
    private val converter: ReportConverter,
) {

    /**
     * 생성 상태를 조회한다. 마감 직후 프론트가 폴링하는 용도다.
     *
     * 생성이 끝나지 않은 보고서도 조회할 수 있어야 하므로 완료 여부를 검사하지 않는다.
     */
    fun getStatus(eventId: Long, currentUserId: Long): ReportStatusResponse {
        val report = findAccessibleReport(eventId, currentUserId)
        return converter.toStatusResponse(report)
    }

    /**
     * 결산 내용을 조회한다.
     *
     * 스냅샷이 없으면 아직 확정 전이다. 보고서 행은 스냅샷 확정 이후에만 만들어지므로
     * 정상적인 경로로는 발생하지 않지만, 방어적으로 생성 중으로 처리한다.
     */
    fun getReport(eventId: Long, currentUserId: Long): ReportDetailResponse {
        val report = findAccessibleReport(eventId, currentUserId)
        val snapshot = report.snapshot ?: throw BusinessException(ErrorCode.REPORT_GENERATING)
        return converter.toDetailResponse(report, snapshot)
    }

    /**
     * 보고서를 찾고 접근 권한을 확인한다.
     *
     * 모임 소속 여부는 스냅샷에 저장된 groupId로 판단한다. 행사를 다시 조회하지 않아도
     * 되고, 보고서 생성 시점의 소속 기준을 그대로 쓰게 된다.
     */
    private fun findAccessibleReport(eventId: Long, currentUserId: Long): Report {
        val report = reportRepository.findByEventId(eventId)
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)

        val groupId = report.snapshot?.event?.groupId
            ?: throw BusinessException(ErrorCode.REPORT_GENERATING)

        if (!groupMembershipProvider.isMember(groupId, currentUserId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        }

        return report
    }
}
