package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.EventCloseResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.ReportGenerationRequester
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
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")

        check(event.status == EventStatus.ACTIVE) { "진행 중인 행사만 마감할 수 있습니다." }
        require(participantCount >= 1) { "행사 참여 인원은 1명 이상이어야 합니다." }

        val persistedEventId = event.id ?: error("저장되지 않은 행사는 마감할 수 없습니다.")
        check(pendingSpendingCountProvider.getPendingSpendingCount(persistedEventId) == 0L) {
            "대기 중인 지출 신청이 있는 행사는 마감할 수 없습니다."
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
        require(groupId > 0) { "모임 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }
        check(groupAccessProvider.existsGroup(groupId)) { "모임을 찾을 수 없습니다. groupId=$groupId" }
        check(groupAccessProvider.isMember(groupId, userId)) { "모임 구성원만 접근할 수 있습니다." }
        check(groupAccessProvider.isOwner(groupId, userId)) { "모임장만 행사를 마감할 수 있습니다." }
    }
}
