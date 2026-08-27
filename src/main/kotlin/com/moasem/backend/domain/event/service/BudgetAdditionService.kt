package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.CreateBudgetAdditionRequest
import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
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
        val event = eventRepository.findByIdAndGroupId(eventId, groupId)
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")
        check(event.status == EventStatus.ACTIVE) { "진행 중인 행사에만 추가 예산을 등록할 수 있습니다." }

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
        require(groupId > 0) { "모임 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }
        check(groupAccessProvider.existsGroup(groupId)) { "모임을 찾을 수 없습니다. groupId=$groupId" }
        check(groupAccessProvider.isMember(groupId, userId)) { "모임 구성원만 접근할 수 있습니다." }
        check(groupAccessProvider.isOwner(groupId, userId)) { "모임장만 추가 예산을 등록할 수 있습니다." }
    }
}
