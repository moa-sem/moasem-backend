package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.EventClosePreviewResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
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
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")

        check(event.status == EventStatus.ACTIVE) { "진행 중인 행사만 마감할 수 있습니다." }
        require(participantCount >= 1) { "행사 참여 인원은 1명 이상이어야 합니다." }

        val persistedEventId = event.id ?: error("저장되지 않은 행사는 마감할 수 없습니다.")
        val pendingSpendingCount = pendingSpendingCountProvider.getPendingSpendingCount(persistedEventId)
        check(pendingSpendingCount == 0L) { "대기 중인 지출 신청이 있는 행사는 마감할 수 없습니다." }

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
        require(groupId > 0) { "모임 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }
        check(groupAccessProvider.existsGroup(groupId)) { "모임을 찾을 수 없습니다. groupId=$groupId" }
        check(groupAccessProvider.isMember(groupId, userId)) { "모임 구성원만 접근할 수 있습니다." }
        check(groupAccessProvider.isOwner(groupId, userId)) { "모임장만 행사 마감을 미리 확인할 수 있습니다." }
    }
}
