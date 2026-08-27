package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.converter.EventConverter
import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.dto.EventDetailResponse
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val groupAccessProvider: GroupAccessProvider,
) {

    @Transactional
    fun createEvent(groupId: Long, currentUserId: Long, request: CreateEventRequest): EventDetailResponse {
        validateGroupOwner(groupId, currentUserId)
        val event = Event.create(
            groupId = groupId,
            title = request.title.trim(),
            description = request.description,
            startAt = requireNotNull(request.startAt) { "행사 시작 시각은 필수입니다." },
            endAt = requireNotNull(request.endAt) { "행사 종료 시각은 필수입니다." },
            initialBudget = request.initialBudget,
        )

        return EventConverter.toDetailResponse(eventRepository.save(event), additionalBudget = 0L)
    }

    @Transactional(readOnly = true)
    fun getEvents(groupId: Long, currentUserId: Long, status: EventStatus? = null): List<EventListResponse> {
        validateGroupMember(groupId, currentUserId)
        val events = if (status == null) {
            eventRepository.findAllByGroupIdOrderByStartAtDesc(groupId)
        } else {
            eventRepository.findAllByGroupIdAndStatusOrderByStartAtDesc(groupId, status)
        }
        return events.map(EventConverter::toListResponse)
    }

    @Transactional(readOnly = true)
    fun getEvent(groupId: Long, eventId: Long, currentUserId: Long): EventDetailResponse {
        validateGroupMember(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupId(eventId, groupId)
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")
        val additionalBudget = budgetAdditionRepository.sumAmountByEventId(event.id!!)
        return EventConverter.toDetailResponse(event, additionalBudget)
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        validateGroupMember(groupId, userId)
        check(groupAccessProvider.isOwner(groupId, userId)) { "모임장만 행사를 생성할 수 있습니다." }
    }

    private fun validateGroupMember(groupId: Long, userId: Long) {
        require(groupId > 0) { "모임 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }
        check(groupAccessProvider.existsGroup(groupId)) { "모임을 찾을 수 없습니다. groupId=$groupId" }
        check(groupAccessProvider.isMember(groupId, userId)) { "모임 구성원만 접근할 수 있습니다." }
    }
}
