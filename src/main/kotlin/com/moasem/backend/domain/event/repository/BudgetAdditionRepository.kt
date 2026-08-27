package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.BudgetAddition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BudgetAdditionRepository : JpaRepository<BudgetAddition, Long> {

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM BudgetAddition b WHERE b.eventId = :eventId")
    fun sumAmountByEventId(@Param("eventId") eventId: Long): Long
}
