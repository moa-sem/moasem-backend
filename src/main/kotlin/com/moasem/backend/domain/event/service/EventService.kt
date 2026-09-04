package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.converter.EventConverter
import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.dto.EventDetailResponse
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val groupAccessProvider: GroupAccessProvider,
    private val approvedSpendingTotalProvider: ApprovedSpendingTotalProvider,
) {

    @Transactional
    fun createEvent(groupId: Long, currentUserId: Long, request: CreateEventRequest): EventDetailResponse {
        validateGroupOwner(groupId, currentUserId)
        validateCreateRequest(request)
        val event = Event.create(
            groupId = groupId,
            title = request.title.trim(),
            description = request.description,
            startAt = checkNotNull(request.startAt),
            endAt = checkNotNull(request.endAt),
            initialBudget = request.initialBudget,
        )

        return EventConverter.toDetailResponse(
            event = eventRepository.save(event),
            additionalBudget = 0L,
            approvedSpending = 0L,
        )
    }

    @Transactional(readOnly = true)
    fun getEvents(groupId: Long, currentUserId: Long, status: EventStatus? = null): List<EventListResponse> {
        validateGroupMember(groupId, currentUserId)
        val events = if (status == null) {
            eventRepository.findAllByGroupIdAndDeletedAtIsNullOrderByStartAtDesc(groupId)
        } else {
            eventRepository.findAllByGroupIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(groupId, status)
        }
        return events.map(EventConverter::toListResponse)
    }

    @Transactional(readOnly = true)
    fun getEvent(groupId: Long, eventId: Long, currentUserId: Long): EventDetailResponse {
        validateGroupMember(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(eventId, groupId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)
        val persistedEventId = event.id ?: error("저장되지 않은 행사는 조회할 수 없습니다.")
        val additionalBudget = budgetAdditionRepository.sumAmountByEventId(persistedEventId)
        val approvedSpending = approvedSpendingTotalProvider.getApprovedSpendingTotal(persistedEventId)
        return EventConverter.toDetailResponse(event, additionalBudget, approvedSpending)
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        validateGroupMember(groupId, userId)
        if (!groupAccessProvider.isOwner(groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 행사를 생성할 수 있습니다.")
        }
    }

    private fun validateGroupMember(groupId: Long, userId: Long) {
        if (groupId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "모임 ID는 양수여야 합니다.")
        if (userId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 양수여야 합니다.")
        if (!groupAccessProvider.existsGroup(groupId)) throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupAccessProvider.isMember(groupId, userId)) throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
    }

    private fun validateCreateRequest(request: CreateEventRequest) {
        if (request.title.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 제목은 비어 있을 수 없습니다.")
        }
        if (request.title.length > Event.TITLE_MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 제목은 ${Event.TITLE_MAX_LENGTH}자 이하여야 합니다.")
        }
        val startAt = request.startAt
            ?: throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 시작 시각은 필수입니다.")
        val endAt = request.endAt
            ?: throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 종료 시각은 필수입니다.")
        if (!startAt.isBefore(endAt)) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 종료 시각은 시작 시각보다 늦어야 합니다.")
        }
        if (request.initialBudget < 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최초 예산은 0원 이상이어야 합니다.")
        }
    }
}
