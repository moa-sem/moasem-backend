package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventRepository : JpaRepository<Event, Long> {

    fun findAllByGroupIdAndDeletedAtIsNullOrderByStartAtDesc(groupId: Long): List<Event>

    fun findAllByGroupIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(
        groupId: Long,
        status: EventStatus,
    ): List<Event>

    fun findByIdAndGroupIdAndDeletedAtIsNull(eventId: Long, groupId: Long): Event?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT e
        FROM Event e
        WHERE e.id = :eventId
          AND e.groupId = :groupId
          AND e.deletedAt IS NULL
        """,
    )
    fun findByIdAndGroupIdAndDeletedAtIsNullForUpdate(
        @Param("eventId") eventId: Long,
        @Param("groupId") groupId: Long,
    ): Event?
}
