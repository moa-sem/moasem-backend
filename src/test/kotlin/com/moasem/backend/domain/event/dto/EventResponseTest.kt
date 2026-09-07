package com.moasem.backend.domain.event.dto

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventResponseTest {

    @Test
    fun `목록 응답 팩토리는 기존 필드를 동일하게 변환한다`() {
        val response = EventListResponse.from(event(EVENT_ID))

        assertThat(response).isEqualTo(
            EventListResponse(
                eventId = EVENT_ID,
                title = TITLE,
                startAt = START_AT,
                endAt = END_AT,
                status = EventStatus.ACTIVE,
                initialBudget = INITIAL_BUDGET,
            ),
        )
        assertThat(EventListResponse::class.members.map { it.name })
            .doesNotContain("description", "additionalBudget", "totalBudget", "approvedSpending", "remainingBudget")
    }

    @Test
    fun `상세 응답 팩토리는 설명과 모든 예산 필드를 동일하게 변환한다`() {
        val response = EventDetailResponse.from(
            event = event(EVENT_ID),
            additionalBudget = 150_000L,
            approvedSpending = 320_000L,
        )

        assertThat(response.eventId).isEqualTo(EVENT_ID)
        assertThat(response.groupId).isEqualTo(GROUP_ID)
        assertThat(response.title).isEqualTo(TITLE)
        assertThat(response.description).isEqualTo(DESCRIPTION)
        assertThat(response.startAt).isEqualTo(START_AT)
        assertThat(response.endAt).isEqualTo(END_AT)
        assertThat(response.status).isEqualTo(EventStatus.ACTIVE)
        assertThat(response.initialBudget).isEqualTo(INITIAL_BUDGET)
        assertThat(response.additionalBudget).isEqualTo(150_000L)
        assertThat(response.totalBudget).isEqualTo(650_000L)
        assertThat(response.approvedSpending).isEqualTo(320_000L)
        assertThat(response.remainingBudget).isEqualTo(330_000L)
    }

    @Test
    fun `저장되지 않은 행사는 목록 응답으로 변환할 수 없다`() {
        assertThatThrownBy { EventListResponse.from(event()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("저장되지 않은 행사는 응답으로 변환할 수 없습니다.")
    }

    @Test
    fun `저장되지 않은 행사는 상세 응답으로 변환할 수 없다`() {
        assertThatThrownBy { EventDetailResponse.from(event(), 0L, 0L) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("저장되지 않은 행사는 응답으로 변환할 수 없습니다.")
    }

    private fun event(id: Long? = null): Event = Event.create(
        groupId = GROUP_ID,
        title = TITLE,
        description = DESCRIPTION,
        startAt = START_AT,
        endAt = END_AT,
        initialBudget = INITIAL_BUDGET,
    ).also { event -> id?.let { assignId(event, it) } }

    private fun assignId(event: Event, id: Long) {
        val idField = Event::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(event, id)
    }

    companion object {
        private const val EVENT_ID = 10L
        private const val GROUP_ID = 1L
        private const val TITLE = "여름 MT"
        private const val DESCRIPTION = "2박 3일 행사"
        private const val INITIAL_BUDGET = 500_000L
        private val START_AT = LocalDateTime.of(2026, 8, 28, 10, 0)
        private val END_AT = LocalDateTime.of(2026, 8, 30, 12, 0)
    }
}
