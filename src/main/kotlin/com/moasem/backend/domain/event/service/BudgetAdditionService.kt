package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.CreateBudgetAdditionRequest
import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BudgetAdditionService(
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val eventRepository: EventRepository,
    private val groupAccessProvider: GroupAccessProvider,
) {

    @Transactional
    fun addBudgetAddition(groupId: Long, eventId: Long, currentUserId: Long, request: CreateBudgetAdditionRequest) {
        validateGroupOwner(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(eventId, groupId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)
        if (event.status != EventStatus.ACTIVE) {
            throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED, "진행 중인 행사에만 추가 예산을 등록할 수 있습니다.")
        }
        validateRequest(request)

        budgetAdditionRepository.save(
            BudgetAddition.create(
                eventId = event.id ?: error("저장되지 않은 행사에는 추가 예산을 등록할 수 없습니다."),
                amount = request.amount,
                reason = request.reason,
                createdBy = currentUserId,
            ),
        )
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        if (groupId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "모임 ID는 양수여야 합니다.")
        if (userId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 양수여야 합니다.")
        if (!groupAccessProvider.existsGroup(groupId)) throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupAccessProvider.isMember(groupId, userId)) throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        if (!groupAccessProvider.isOwner(groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 추가 예산을 등록할 수 있습니다.")
        }
    }

    private fun validateRequest(request: CreateBudgetAdditionRequest) {
        if (request.amount <= 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "추가 예산 금액은 0원보다 커야 합니다.")
        }
        if (request.reason.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "추가 예산 사유는 비어 있을 수 없습니다.")
        }
    }
}
