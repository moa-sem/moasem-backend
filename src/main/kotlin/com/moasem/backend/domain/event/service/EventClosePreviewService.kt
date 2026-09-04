package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.EventClosePreviewResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventClosePreviewService(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val groupAccessProvider: GroupAccessProvider,
    private val pendingSpendingCountProvider: PendingSpendingCountProvider,
    private val approvedSpendingTotalProvider: ApprovedSpendingTotalProvider,
) {

    @Transactional(readOnly = true)
    fun previewClose(
        groupId: Long,
        eventId: Long,
        currentUserId: Long,
        participantCount: Int,
    ): EventClosePreviewResponse {
        validateGroupOwner(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(eventId, groupId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        if (event.status != EventStatus.ACTIVE) throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)
        if (participantCount < 1) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 참여 인원은 1명 이상이어야 합니다.")
        }

        val persistedEventId = event.id ?: error("저장되지 않은 행사는 마감할 수 없습니다.")
        val pendingSpendingCount = pendingSpendingCountProvider.getPendingSpendingCount(persistedEventId)
        if (pendingSpendingCount != 0L) throw BusinessException(ErrorCode.EVENT_HAS_PENDING_SPENDING)

        val additionalBudget = budgetAdditionRepository.sumAmountByEventId(persistedEventId)
        val approvedSpending = approvedSpendingTotalProvider.getApprovedSpendingTotal(persistedEventId)
        val totalBudget = event.initialBudget + additionalBudget

        return EventClosePreviewResponse(
            eventId = persistedEventId,
            title = event.title,
            status = event.status,
            participantCount = participantCount,
            pendingSpendingCount = pendingSpendingCount,
            initialBudget = event.initialBudget,
            additionalBudget = additionalBudget,
            totalBudget = totalBudget,
            approvedSpending = approvedSpending,
            remainingBudget = totalBudget - approvedSpending,
        )
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        if (groupId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "모임 ID는 양수여야 합니다.")
        if (userId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 양수여야 합니다.")
        if (!groupAccessProvider.existsGroup(groupId)) throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupAccessProvider.isMember(groupId, userId)) throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        if (!groupAccessProvider.isOwner(groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 행사 마감을 미리 확인할 수 있습니다.")
        }
    }
}
