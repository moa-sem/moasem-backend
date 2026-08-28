package com.moasem.backend.domain.spending.repository

import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SpendingRepository : JpaRepository<Spending, Long> {

    /** 경로의 행사와 지출이 실제로 이어져 있는지까지 한 번에 확인한다. */
    fun findByIdAndEventId(id: Long, eventId: Long): Spending?

    fun findAllByEventId(eventId: Long, pageable: Pageable): Page<Spending>

    fun findAllByEventIdAndStatus(eventId: Long, status: SpendingStatus, pageable: Pageable): Page<Spending>
}
