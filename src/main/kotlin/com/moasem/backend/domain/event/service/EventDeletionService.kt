package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
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
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")

        check(event.status == EventStatus.ACTIVE) { "진행 중인 행사만 삭제할 수 있습니다." }
        check(!spendingHistoryProvider.hasAnySpending(eventId)) {
            "지출 신청 이력이 있는 행사는 삭제할 수 없습니다."
        }

        event.delete()
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        require(groupId > 0) { "모임 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }
        check(groupAccessProvider.existsGroup(groupId)) { "모임을 찾을 수 없습니다. groupId=$groupId" }
        check(groupAccessProvider.isMember(groupId, userId)) { "모임 구성원만 접근할 수 있습니다." }
        check(groupAccessProvider.isOwner(groupId, userId)) { "모임장만 행사를 삭제할 수 있습니다." }
    }
}
