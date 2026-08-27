package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventService = EventService(eventRepository, groupAccessProvider)
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
    }

    @Nested
    @DisplayName("행사 생성")
    inner class CreateEvent {

        @Test
        fun `모임장이 유효한 요청으로 행사를 생성하면 ACTIVE 상태로 저장한다`() {
            val savedEvent = slot<Event>()
            every { eventRepository.save(capture(savedEvent)) } answers {
                assignId(savedEvent.captured, EVENT_ID)
                savedEvent.captured
            }

            val response = eventService.createEvent(GROUP_ID, OWNER_ID, createRequest())

            assertThat(savedEvent.captured.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(savedEvent.captured.title).isEqualTo("여름 MT")
            assertThat(savedEvent.captured.description).isNull()
            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.initialBudget).isEqualTo(500_000L)
        }

        @Test
        fun `공백 설명은 null로 저장한다`() {
            val savedEvent = saveEvent()

            eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(description = "   "))

            assertThat(savedEvent.captured.description).isNull()
        }

        @Test
        fun `입력한 설명은 상세 응답에 포함한다`() {
            saveEvent()

            val response = eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(description = "2박 3일 여름 MT입니다."))

            assertThat(response.description).isEqualTo("2박 3일 여름 MT입니다.")
        }

        @Test
        fun `공백 제목은 생성하지 않는다`() {
            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(title = "   ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("제목")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `100자 초과 제목은 생성하지 않는다`() {
            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(title = "가".repeat(101))) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("100자")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `음수 최초 예산은 생성하지 않는다`() {
            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(initialBudget = -1L)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("최초 예산")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `최초 예산 0원으로 행사를 생성할 수 있다`() {
            val savedEvent = saveEvent()

            val response = eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(initialBudget = 0L))

            assertThat(savedEvent.captured.initialBudget).isZero()
            assertThat(response.initialBudget).isZero()
        }

        @Test
        fun `존재하지 않는 모임이면 저장하지 않는다`() {
            every { groupAccessProvider.existsGroup(GROUP_ID) } returns false

            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임을 찾을 수 없습니다")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `모임 구성원이 아니면 저장하지 않는다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임 구성원만")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `OWNER가 아니면 저장하지 않는다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임장만")

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `종료 시각이 시작 시각보다 이르면 생성하지 않는다`() {
            val startAt = LocalDateTime.of(2026, 8, 28, 10, 0)

            assertThatThrownBy {
                eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(startAt = startAt, endAt = startAt))
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("종료 시각")

            verify(exactly = 0) { eventRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("행사 목록 조회")
    inner class GetEvents {

        @Test
        fun `상태가 없으면 모임의 전체 행사를 조회한다`() {
            every { eventRepository.findAllByGroupIdOrderByStartAtDesc(GROUP_ID) } returns listOf(event(EVENT_ID))

            val responses = eventService.getEvents(GROUP_ID, OWNER_ID)

            assertThat(responses.map { it.eventId }).containsExactly(EVENT_ID)
            verify { eventRepository.findAllByGroupIdOrderByStartAtDesc(GROUP_ID) }
        }

        @Test
        fun `상태가 있으면 해당 상태의 행사만 조회한다`() {
            every {
                eventRepository.findAllByGroupIdAndStatusOrderByStartAtDesc(GROUP_ID, EventStatus.CLOSED)
            } returns listOf(event(EVENT_ID, EventStatus.CLOSED))

            val responses = eventService.getEvents(GROUP_ID, OWNER_ID, EventStatus.CLOSED)

            assertThat(responses).allSatisfy { assertThat(it.status).isEqualTo(EventStatus.CLOSED) }
            verify { eventRepository.findAllByGroupIdAndStatusOrderByStartAtDesc(GROUP_ID, EventStatus.CLOSED) }
        }
    }

    @Nested
    @DisplayName("행사 상세 조회")
    inner class GetEvent {

        @Test
        fun `모임 구성원은 해당 모임의 행사 상세를 조회한다`() {
            every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.groupId).isEqualTo(GROUP_ID)
        }

        @Test
        fun `행사가 없거나 다른 모임 행사이면 예외가 발생한다`() {
            every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("행사를 찾을 수 없습니다")
        }
    }

    private fun createRequest(
        title: String = "여름 MT",
        description: String? = null,
        startAt: LocalDateTime = LocalDateTime.of(2026, 8, 28, 10, 0),
        endAt: LocalDateTime = LocalDateTime.of(2026, 8, 30, 12, 0),
        initialBudget: Long = 500_000L,
    ) = CreateEventRequest(
        title = title,
        description = description,
        startAt = startAt,
        endAt = endAt,
        initialBudget = initialBudget,
    )

    private fun event(id: Long, status: EventStatus = EventStatus.ACTIVE): Event =
        Event.create(
            GROUP_ID,
            "여름 MT",
            null,
            LocalDateTime.of(2026, 8, 28, 10, 0),
            LocalDateTime.of(2026, 8, 30, 12, 0),
            500_000L,
        )
            .also { event ->
                assignId(event, id)
                if (status == EventStatus.CLOSED) {
                    val statusField = Event::class.java.getDeclaredField("status")
                    statusField.isAccessible = true
                    statusField.set(event, status)
                }
            }

    private fun saveEvent(): io.mockk.CapturingSlot<Event> {
        val savedEvent = slot<Event>()
        every { eventRepository.save(capture(savedEvent)) } answers {
            assignId(savedEvent.captured, EVENT_ID)
            savedEvent.captured
        }
        return savedEvent
    }

    private fun assignId(event: Event, id: Long) {
        val idField = Event::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(event, id)
    }

    companion object {
        private const val GROUP_ID = 1L
        private const val OWNER_ID = 10L
        private const val EVENT_ID = 100L
    }
}
