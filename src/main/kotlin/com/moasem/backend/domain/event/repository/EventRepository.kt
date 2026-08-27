package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import org.springframework.data.jpa.repository.JpaRepository

interface EventRepository : JpaRepository<Event, Long> {

    fun findAllByGroupIdAndDeletedAtIsNullOrderByStartAtDesc(groupId: Long): List<Event>

    fun findAllByGroupIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(
        groupId: Long,
        status: EventStatus,
    ): List<Event>

    fun findByIdAndGroupIdAndDeletedAtIsNull(eventId: Long, groupId: Long): Event?
}
