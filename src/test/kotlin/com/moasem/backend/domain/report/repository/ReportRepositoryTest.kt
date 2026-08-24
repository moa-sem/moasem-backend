package com.moasem.backend.domain.report.repository

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportSnapshot
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportRepositoryTest @Autowired constructor(
    private val reportRepository: ReportRepository,
    private val entityManager: EntityManager,
) {

    @Test
    @DisplayName("결산 스냅샷이 JSONB로 저장되고 그대로 복원된다")
    fun snapshotRoundTrip() {
        val report = Report.create(eventId = 1L)
        report.applySnapshot(snapshot())
        reportRepository.saveAndFlush(report)
        entityManager.clear()

        val found = reportRepository.findByEventId(1L)

        assertThat(found).isNotNull
        assertThat(found!!.snapshot).isEqualTo(snapshot())
        assertThat(found.snapshot!!.event.startAt).isEqualTo(LocalDateTime.of(2026, 8, 24, 19, 0))
        assertThat(found.totalBudget).isEqualTo(500_000L)
    }

    @Test
    @DisplayName("스냅샷의 날짜는 사람이 읽을 수 있는 ISO 문자열로 저장된다")
    fun snapshotStoresReadableDates() {
        val report = Report.create(eventId = 3L)
        report.applySnapshot(snapshot())
        reportRepository.saveAndFlush(report)

        val rawJson = entityManager
            .createNativeQuery("SELECT snapshot::text FROM reports WHERE event_id = 3")
            .singleResult as String

        // epoch 숫자로 저장되면 나중에 DB에서 직접 들여다볼 때 해석이 불가능하다.
        assertThat(rawJson).contains("2026-08-24T19:00:00")
    }

    @Test
    @DisplayName("같은 행사로 보고서를 두 건 만들 수 없다")
    fun oneReportPerEvent() {
        reportRepository.saveAndFlush(Report.create(eventId = 2L))

        assertThatThrownBy {
            reportRepository.saveAndFlush(Report.create(eventId = 2L))
        }.isInstanceOf(Exception::class.java)
    }

    private fun snapshot(): ReportSnapshot {
        val now = LocalDateTime.of(2026, 8, 24, 19, 0)
        return ReportSnapshot(
            event = ReportSnapshot.EventSummary(1L, "여름 MT", now, now.plusDays(2), 10L, "백엔드 스터디"),
            budget = ReportSnapshot.BudgetSummary(500_000L, emptyList(), 500_000L, 320_000L, 180_000L),
            tagTotals = listOf(ReportSnapshot.TagTotal("MEAL", "식비", 320_000L, 1)),
            spendings = listOf(
                ReportSnapshot.SpendingLine(100L, "저녁 식사", 320_000L, "MEAL", "김석주", now.plusDays(1), null),
            ),
        )
    }
}
