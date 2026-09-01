package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.spending.service.port.EventAccess
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * spending 도메인이 요구하는 행사 조회 경계의 구현.
 *
 * 행사 데이터를 가진 event 도메인이 남의 port를 구현한다. spending은 [EventRepository]나
 * [com.moasem.backend.domain.event.entity.Event]를 알 필요가 없다.
 *
 * 삭제된 행사는 없는 것으로 취급한다. 소프트 삭제라 행은 남아 있지만, 지출을 새로 받거나
 * 기존 지출을 열어 볼 대상은 아니다.
 */
@Component
class EventAccessAdapter(
    private val eventRepository: EventRepository,
) : EventAccessProvider {

    @Transactional(readOnly = true)
    override fun findAccess(eventId: Long): EventAccess? {
        val event = eventRepository.findById(eventId).orElse(null) ?: return null
        if (event.isDeleted) return null

        return EventAccess(
            eventId = eventId,
            groupId = event.groupId,
            isActive = event.status == EventStatus.ACTIVE,
        )
    }
}
