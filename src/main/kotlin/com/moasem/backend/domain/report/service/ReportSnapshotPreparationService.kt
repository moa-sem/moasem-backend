package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.event.service.port.EventSettlementSnapshot
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.EventSnapshotProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 행사 마감 트랜잭션에 참여해 보고서 행과 결산 스냅샷을 함께 확정한다. */
@Service
class ReportSnapshotPreparationService(
    private val reportRepository: ReportRepository,
    private val eventSnapshotProvider: EventSnapshotProvider,
    private val snapshotCalculator: ReportSnapshotCalculator,
) {

    @Transactional
    fun prepare(eventId: Long, settlement: EventSettlementSnapshot): Report {
        if (reportRepository.existsByEventId(eventId)) {
            throw BusinessException(ErrorCode.REPORT_ALREADY_EXISTS)
        }

        val snapshot = snapshotCalculator.calculate(eventSnapshotProvider.fetch(eventId), settlement)
        return reportRepository.save(Report.create(eventId).also { it.applySnapshot(snapshot) })
    }
}
