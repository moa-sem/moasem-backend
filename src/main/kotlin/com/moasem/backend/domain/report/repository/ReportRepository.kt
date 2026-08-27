package com.moasem.backend.domain.report.repository

import com.moasem.backend.domain.report.entity.Report
import org.springframework.data.jpa.repository.JpaRepository

interface ReportRepository : JpaRepository<Report, Long> {

    fun findByEventId(eventId: Long): Report?

    fun existsByEventId(eventId: Long): Boolean
}
