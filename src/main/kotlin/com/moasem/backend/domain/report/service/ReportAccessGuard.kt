package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Component

/**
 * 보고서를 찾고 접근 권한을 확인한다.
 *
 * 조회·다운로드·재시도가 모두 같은 기준을 쓴다. 서비스마다 따로 두면 한 곳만 고쳐졌을 때
 * 조용히 뚫린다. 권한 검증은 빠져도 테스트가 통과해 버리는 종류라 한곳에 모은다.
 */
@Component
class ReportAccessGuard(
    private val reportRepository: ReportRepository,
    private val groupMembershipProvider: GroupMembershipProvider,
) {

    /**
     * 모임 소속 여부는 스냅샷에 저장된 groupId로 판단한다. 행사를 다시 조회하지 않아도 되고,
     * 보고서 생성 시점의 소속 기준을 그대로 쓰게 된다.
     *
     * 스냅샷이 없으면 아직 확정 전이다. 보고서 행은 스냅샷 확정 이후에만 만들어지므로
     * 정상적인 경로로는 발생하지 않지만, 소속을 판단할 근거가 없으므로 통과시키지 않는다.
     */
    fun findAccessibleReport(eventId: Long, currentUserId: Long): Report {
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
