package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventDeletionService(
    private val eventRepository: EventRepository,
    private val groupAccessProvider: GroupAccessProvider,
    private val spendingHistoryProvider: SpendingHistoryProvider,
) {

    @Transactional
    fun deleteEvent(groupId: Long, eventId: Long, currentUserId: Long) {
        validateGroupOwner(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(eventId, groupId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        if (event.status != EventStatus.ACTIVE) {
            throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED, "진행 중인 행사만 삭제할 수 있습니다.")
        }
        if (spendingHistoryProvider.hasAnySpending(eventId)) {
            throw BusinessException(ErrorCode.EVENT_HAS_SPENDING_HISTORY)
        }

        event.delete()
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        if (groupId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "모임 ID는 양수여야 합니다.")
        if (userId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 양수여야 합니다.")
        if (!groupAccessProvider.existsGroup(groupId)) throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupAccessProvider.isMember(groupId, userId)) throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        if (!groupAccessProvider.isOwner(groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 행사를 삭제할 수 있습니다.")
        }
    }
}
