package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.EventCloseResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.ReportGenerationRequester
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class EventCloseService(
    private val eventRepository: EventRepository,
    private val groupAccessProvider: GroupAccessProvider,
    private val pendingSpendingCountProvider: PendingSpendingCountProvider,
    private val reportGenerationRequester: ReportGenerationRequester,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun closeEvent(
        groupId: Long,
        eventId: Long,
        currentUserId: Long,
        participantCount: Int,
    ): EventCloseResponse {
        validateGroupOwner(groupId, currentUserId)
        val event = eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(eventId, groupId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        if (event.status != EventStatus.ACTIVE) throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)
        if (participantCount < 1) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사 참여 인원은 1명 이상이어야 합니다.")
        }

        val persistedEventId = event.id ?: error("저장되지 않은 행사는 마감할 수 없습니다.")
        if (pendingSpendingCountProvider.getPendingSpendingCount(persistedEventId) != 0L) {
            throw BusinessException(ErrorCode.EVENT_HAS_PENDING_SPENDING)
        }

        event.close(participantCount)
        requestReportGenerationAfterCommit(persistedEventId)

        return EventCloseResponse(
            eventId = persistedEventId,
            status = event.status,
            participantCount = checkNotNull(event.participantCount),
            closedAt = checkNotNull(event.closedAt),
        )
    }

    private fun requestReportGenerationAfterCommit(eventId: Long) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        reportGenerationRequester.requestReportGeneration(eventId)
                    } catch (exception: Exception) {
                        log.error("마감 후 보고서 생성 요청 실패. eventId={}", eventId, exception)
                    }
                }
            },
        )
    }

    private fun validateGroupOwner(groupId: Long, userId: Long) {
        if (groupId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "모임 ID는 양수여야 합니다.")
        if (userId <= 0) throw BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 양수여야 합니다.")
        if (!groupAccessProvider.existsGroup(groupId)) throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (!groupAccessProvider.isMember(groupId, userId)) throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        if (!groupAccessProvider.isOwner(groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_OWNER, "모임장만 행사를 마감할 수 있습니다.")
        }
    }
}
