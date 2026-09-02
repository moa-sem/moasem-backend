package com.moasem.backend.domain.spending.service

import com.moasem.backend.domain.spending.converter.SpendingConverter
import com.moasem.backend.domain.spending.dto.RejectSpendingRequest
import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.repository.SpendingRepository
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지출 승인·반려.
 *
 * 신청·조회와 달리 모임장만 호출할 수 있고, 같은 건에 요청이 겹치면 한 번만 처리되어야 해서
 * 별도 서비스로 둔다. 상태 전이 규칙 자체는 [Spending]이 강제하고 여기서는 권한과 동시성만 다룬다.
 */
@Service
class SpendingApprovalService(
    private val spendingRepository: SpendingRepository,
    private val eventAccessProvider: EventAccessProvider,
    private val groupAccessProvider: GroupAccessProvider,
) {

    /**
     * 지출을 승인한다.
     *
     * 승인 후 잔여 예산이 음수가 되어도 막지 않는다. 초과 지출은 결산에서 드러낼 값이지
     * 승인을 거부할 조건이 아니다(기획안 8.2).
     */
    @Transactional
    fun approve(eventId: Long, spendingId: Long, currentUserId: Long): SpendingDetailResponse {
        validateOwner(eventId, currentUserId)

        val spending = findForProcessing(eventId, spendingId)
        spending.approve(currentUserId)

        return SpendingConverter.toDetailResponse(spending)
    }

    /** 지출을 반려한다. 사유는 필수이며 비어 있으면 엔티티가 거부한다. */
    @Transactional
    fun reject(
        eventId: Long,
        spendingId: Long,
        currentUserId: Long,
        request: RejectSpendingRequest,
    ): SpendingDetailResponse {
        validateOwner(eventId, currentUserId)

        val spending = findForProcessing(eventId, spendingId)
        spending.reject(currentUserId, request.reason)

        return SpendingConverter.toDetailResponse(spending)
    }

    private fun validateOwner(eventId: Long, userId: Long) {
        require(eventId > 0) { "행사 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }

        val access = eventAccessProvider.findAccess(eventId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)
        if (!groupAccessProvider.isOwner(access.groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 지출을 처리할 수 있습니다.")
        }
    }

    /**
     * 처리 대상을 잠근 채 가져온다.
     *
     * 락 없이 읽으면 동시에 들어온 승인과 반려가 둘 다 PENDING을 보고 각자 처리해버린다.
     * 뒤 트랜잭션은 여기서 대기했다가 갱신된 상태를 읽고, 엔티티의 PENDING 검사에 걸린다.
     */
    private fun findForProcessing(eventId: Long, spendingId: Long): Spending =
        spendingRepository.findWithLockByIdAndEventId(spendingId, eventId)
            ?: throw BusinessException(ErrorCode.SPENDING_NOT_FOUND)
}
