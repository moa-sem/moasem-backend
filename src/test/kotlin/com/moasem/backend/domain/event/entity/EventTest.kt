package com.moasem.backend.domain.event.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventTest {

    @Test
    fun `추가 예산이 없으면 총예산은 최초 예산과 같다`() {
        assertThat(event().calculateTotalBudget(0L)).isEqualTo(500_000L)
    }

    @Test
    fun `총예산은 최초 예산과 추가 예산의 합이다`() {
        assertThat(event().calculateTotalBudget(150_000L)).isEqualTo(650_000L)
    }

    @Test
    fun `잔여 예산은 총예산에서 승인 지출을 차감한다`() {
        assertThat(event().calculateRemainingBudget(150_000L, 320_000L)).isEqualTo(330_000L)
    }

    @Test
    fun `승인 지출이 총예산과 같으면 잔여 예산은 0원이다`() {
        assertThat(event().calculateRemainingBudget(150_000L, 650_000L)).isZero()
    }

    @Test
    fun `승인 지출이 총예산보다 크면 음수 잔여 예산을 유지한다`() {
        assertThat(event().calculateRemainingBudget(0L, 550_000L)).isEqualTo(-50_000L)
    }

    @Test
    fun `예산 계산은 행사 상태를 변경하지 않는다`() {
        val event = event()

        event.calculateTotalBudget(150_000L)
        event.calculateRemainingBudget(150_000L, 320_000L)

        assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
        assertThat(event.deletedAt).isNull()
        assertThat(event.participantCount).isNull()
        assertThat(event.closedAt).isNull()
    }

    @Test
    fun `ACTIVE 행사를 마감하면 상태 참여 인원 마감 시각을 기록한다`() {
        val event = event()
        val beforeClose = LocalDateTime.now()

        event.close(PARTICIPANT_COUNT)

        assertThat(event.status).isEqualTo(EventStatus.CLOSED)
        assertThat(event.participantCount).isEqualTo(PARTICIPANT_COUNT)
        assertThat(event.closedAt).isAfterOrEqualTo(beforeClose)
        assertThat(event.closedAt).isBeforeOrEqualTo(LocalDateTime.now())
    }

    @Test
    fun `이미 CLOSED인 행사는 다시 마감할 수 없다`() {
        val event = event()
        event.close(PARTICIPANT_COUNT)
        val firstClosedAt = event.closedAt

        assertThatThrownBy { event.close(PARTICIPANT_COUNT + 1) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("진행 중인 행사")

        assertThat(event.participantCount).isEqualTo(PARTICIPANT_COUNT)
        assertThat(event.closedAt).isEqualTo(firstClosedAt)
    }

    @Test
    fun `참여 인원이 0명 또는 음수이면 상태와 마감 정보를 변경하지 않는다`() {
        listOf(0, -1).forEach { invalidParticipantCount ->
            val event = event()

            assertThatThrownBy { event.close(invalidParticipantCount) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("1명 이상")

            assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(event.participantCount).isNull()
            assertThat(event.closedAt).isNull()
        }
    }

    private fun event(): Event = Event.create(
        groupId = 1L,
        title = "여름 MT",
        description = null,
        startAt = LocalDateTime.of(2026, 8, 28, 10, 0),
        endAt = LocalDateTime.of(2026, 8, 30, 12, 0),
        initialBudget = 500_000L,
    )

    companion object {
        private const val PARTICIPANT_COUNT = 3
    }
}
