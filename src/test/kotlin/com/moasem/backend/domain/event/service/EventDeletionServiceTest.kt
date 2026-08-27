package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventDeletionServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private val spendingHistoryProvider = mockk<SpendingHistoryProvider>()
    private lateinit var eventDeletionService: EventDeletionService

    @BeforeEach
    fun setUp() {
        eventDeletionService = EventDeletionService(eventRepository, groupAccessProvider, spendingHistoryProvider)
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
        every { spendingHistoryProvider.hasAnySpending(EVENT_ID) } returns false
    }

    @Nested
    @DisplayName("행사 조건부 논리 삭제")
    inner class DeleteEvent {

        @Test
        fun `OWNER는 지출 이력이 없는 ACTIVE 행사를 논리 삭제한다`() {
            val event = event(EVENT_ID)
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event

            eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(event.deletedAt).isNotNull()
            assertThat(event.isDeleted).isTrue()
            verify(exactly = 0) { eventRepository.delete(any<Event>()) }
        }

        @Test
        fun `MEMBER는 행사를 삭제할 수 없다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임장만")

            verify(exactly = 0) { eventRepository.delete(any<Event>()) }
        }

        @Test
        fun `비소속 사용자는 행사를 삭제할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임 구성원만")
        }

        @Test
        fun `존재하지 않는 모임의 행사는 삭제할 수 없다`() {
            every { groupAccessProvider.existsGroup(GROUP_ID) } returns false

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임을 찾을 수 없습니다")
        }

        @Test
        fun `존재하지 않는 행사는 삭제할 수 없다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("행사를 찾을 수 없습니다")
        }

        @Test
        fun `다른 모임에 속한 행사는 삭제할 수 없다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)
        }

        @Test
        fun `CLOSED 행사는 삭제할 수 없다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID)
            } returns event(EVENT_ID, EventStatus.CLOSED)

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("진행 중인 행사")
        }

        @Test
        fun `상태와 관계없이 지출 신청 이력이 한 건이라도 있으면 삭제를 차단한다`() {
            every { spendingHistoryProvider.hasAnySpending(EVENT_ID) } returns true

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("지출 신청 이력")

            verify { spendingHistoryProvider.hasAnySpending(EVENT_ID) }
            verify(exactly = 0) { eventRepository.delete(any<Event>()) }
        }

        @Test
        fun `논리 삭제된 행사는 재삭제 대상에서 제외한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)

            verify { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) }
        }
    }

    private fun event(id: Long, status: EventStatus = EventStatus.ACTIVE): Event =
        Event.create(
            groupId = GROUP_ID,
            title = "여름 MT",
            description = null,
            startAt = LocalDateTime.of(2026, 8, 28, 10, 0),
            endAt = LocalDateTime.of(2026, 8, 30, 12, 0),
            initialBudget = 500_000L,
        ).also { event ->
            Event::class.java.getDeclaredField("id").apply {
                isAccessible = true
                set(event, id)
            }
            if (status == EventStatus.CLOSED) {
                Event::class.java.getDeclaredField("status").apply {
                    isAccessible = true
                    set(event, status)
                }
            }
        }

    companion object {
        private const val GROUP_ID = 1L
        private const val OWNER_ID = 10L
        private const val EVENT_ID = 100L
    }
}
