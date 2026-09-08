package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.BudgetAddition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BudgetAdditionRepository : JpaRepository<BudgetAddition, Long> {

    /** 결산 보고서가 예산 추가를 한 건씩 찍을 때 쓴다. 표시 순서는 report가 등록 시각으로 정한다. */
    fun findAllByEventId(eventId: Long): List<BudgetAddition>

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM BudgetAddition b WHERE b.eventId = :eventId")
    fun sumAmountByEventId(@Param("eventId") eventId: Long): Long
}
