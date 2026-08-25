package com.moasem.backend.domain.report.service.port

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * port 구현이 지켜야 할 계약을 fake로 검증한다.
 *
 * 실제 어댑터(S3, AI, event 도메인)를 붙일 때도 같은 계약을 만족해야 한다.
 */
class PortContractTest {

    @Nested
    @DisplayName("EventSnapshotProvider")
    inner class EventSnapshot {

        @Test
        fun `행사 원자료를 조회한다`() {
            val provider = FakeEventSnapshotProvider()
            provider.given(FakeEventSnapshotProvider.sampleData(eventId = 1L))

            val data = provider.fetch(1L)

            assertThat(data.eventId).isEqualTo(1L)
            assertThat(data.title).isEqualTo("여름 MT")
            assertThat(data.initialBudget).isEqualTo(500_000L)
        }

        @Test
        fun `없는 행사를 조회하면 예외가 발생한다`() {
            val provider = FakeEventSnapshotProvider()

            assertThatThrownBy { provider.fetch(999L) }
                .isInstanceOf(NoSuchElementException::class.java)
        }

        @Test
        fun `참여 인원은 아직 확정되지 않아 없을 수 있다`() {
            val provider = FakeEventSnapshotProvider()
            provider.given(FakeEventSnapshotProvider.sampleData(participantCount = null))

            assertThat(provider.fetch(1L).participantCount).isNull()
        }
    }

    @Nested
    @DisplayName("ReportFileStorage")
    inner class FileStorage {

        @Test
        fun `업로드한 파일을 그대로 다시 읽을 수 있다`() {
            val storage = FakeReportFileStorage()
            val content = "결산 보고서".toByteArray()

            val key = storage.upload("reports/1/report.pdf", content, "application/pdf")

            assertThat(key).isEqualTo("reports/1/report.pdf")
            assertThat(storage.read(key)!!.content).isEqualTo(content)
            assertThat(storage.read(key)!!.contentType).isEqualTo("application/pdf")
        }

        @Test
        fun `저장된 파일에 대해 다운로드 URL을 발급한다`() {
            val storage = FakeReportFileStorage()
            storage.upload("reports/1/report.csv", "a,b,c".toByteArray(), "text/csv")

            val url = storage.generateDownloadUrl("reports/1/report.csv", Duration.ofMinutes(5))

            assertThat(url).contains("reports/1/report.csv")
        }

        @Test
        fun `저장되지 않은 파일의 URL은 발급할 수 없다`() {
            val storage = FakeReportFileStorage()

            assertThatThrownBy { storage.generateDownloadUrl("없는키", Duration.ofMinutes(5)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("ReportAiClient")
    inner class AiClient {

        @Test
        fun `집계값을 받아 분석 코멘트를 반환한다`() {
            val client = FakeReportAiClient()

            val result = client.analyze(sampleInput())

            assertThat(result).isEqualTo(FakeReportAiClient.DEFAULT_RESPONSE)
            assertThat(client.callCount).isEqualTo(1)
        }

        @Test
        fun `실패하면 예외를 던진다`() {
            val client = FakeReportAiClient()
            client.failWith("타임아웃")

            assertThatThrownBy { client.analyze(sampleInput()) }
                .isInstanceOf(ReportAiException::class.java)
                .hasMessageContaining("타임아웃")
        }

        @Test
        @DisplayName("입력에 개별 지출 내역이 포함되지 않는다")
        fun inputCarriesOnlyAggregates() {
            val client = FakeReportAiClient()

            client.analyze(sampleInput())

            // AiAnalysisInput에는 집계값만 있다. 개별 지출을 넘길 수 있는 필드가 존재하면
            // AI가 금액을 재계산할 여지가 생기므로, 타입 수준에서 막혀 있어야 한다.
            val fields = AiAnalysisInput::class.members.map { it.name }
            assertThat(fields).doesNotContain("spendings", "approvedSpendings")
            assertThat(client.lastInput!!.tagTotals).isNotEmpty()
        }

        private fun sampleInput() = AiAnalysisInput(
            eventTitle = "여름 MT",
            totalBudget = 500_000L,
            totalSpent = 320_000L,
            remainingBalance = 180_000L,
            tagTotals = listOf(TagTotalData(tag = "MEAL", label = "식비", amount = 320_000L, count = 1)),
        )
    }
}
