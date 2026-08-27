package com.moasem.backend.domain.spending.repository

import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SpendingRepository : JpaRepository<Spending, Long> {

    fun findAllByEventId(eventId: Long, pageable: Pageable): Page<Spending>

    fun findAllByEventIdAndStatus(eventId: Long, status: SpendingStatus, pageable: Pageable): Page<Spending>
}
