package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.event.service.port.EventSettlementSnapshot
import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleAddition
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleData
import com.moasem.backend.domain.report.service.port.FakeEventSnapshotProvider.Companion.sampleSpending
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReportSnapshotPreparationServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val snapshotProvider = FakeEventSnapshotProvider()
    private lateinit var service: ReportSnapshotPreparationService

    @BeforeEach
    fun setUp() {
        snapshotProvider.given(
            sampleData(
                eventId = EVENT_ID,
                initialBudget = 100L,
                budgetAdditions = listOf(sampleAddition(amount = 20L)),
                approvedSpendings = listOf(sampleSpending(amount = 30L)),
            ),
        )
        every { reportRepository.existsByEventId(EVENT_ID) } returns false
        every { reportRepository.save(any()) } answers { firstArg() }
        service = ReportSnapshotPreparationService(
            reportRepository,
            snapshotProvider,
            ReportSnapshotCalculator(),
        )
    }

    @Test
    fun `마감에서 확정한 결산값을 원자료 재집계 값 대신 저장한다`() {
        val saved = slot<Report>()
        every { reportRepository.save(capture(saved)) } answers { saved.captured }

        service.prepare(EVENT_ID, settlement())

        val snapshot = checkNotNull(saved.captured.snapshot)
        assertThat(snapshot.budget.totalBudget).isEqualTo(1_000L)
        assertThat(snapshot.budget.totalSpent).isEqualTo(1_001L)
        assertThat(snapshot.budget.remainingBalance).isEqualTo(-1L)
        assertThat(snapshot.event.participantCount).isEqualTo(3)
        assertThat(snapshot.settlement?.additionalChargePerPerson).isEqualTo(1L)
        assertThat(snapshot.settlement?.overCollectedAmount).isEqualTo(2L)
        assertThat(snapshot.settlement?.closedAt).isEqualTo(CLOSED_AT)
        assertThat(snapshot.spendings).hasSize(1)
    }

    @Test
    fun `이미 보고서 행이 있으면 스냅샷을 중복 생성하지 않는다`() {
        every { reportRepository.existsByEventId(EVENT_ID) } returns true

        assertThatThrownBy { service.prepare(EVENT_ID, settlement()) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_EXISTS)

        verify(exactly = 0) { reportRepository.save(any()) }
    }

    private fun settlement() = EventSettlementSnapshot(
        totalBudget = 1_000L,
        approvedSpending = 1_001L,
        remainingBudget = -1L,
        participantCount = 3,
        refundPerPerson = 0L,
        refundRemainder = 0L,
        additionalChargePerPerson = 1L,
        overCollectedAmount = 2L,
        closedAt = CLOSED_AT,
    )

    companion object {
        private const val EVENT_ID = 1L
        private val CLOSED_AT = LocalDateTime.of(2026, 11, 6, 12, 0)
    }
}
