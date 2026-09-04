package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.hasErrorCode
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
    private val budgetAdditionRepository = mockk<BudgetAdditionRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private val approvedSpendingTotalProvider = mockk<ApprovedSpendingTotalProvider>()
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        eventService = EventService(
            eventRepository,
            budgetAdditionRepository,
            groupAccessProvider,
            approvedSpendingTotalProvider,
        )
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 0L
        every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 0L
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
            assertThat(savedEvent.captured.deletedAt).isNull()
            assertThat(savedEvent.captured.title).isEqualTo("여름 MT")
            assertThat(savedEvent.captured.description).isNull()
            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.initialBudget).isEqualTo(500_000L)
            assertThat(response.approvedSpending).isZero()
            assertThat(response.remainingBudget).isEqualTo(500_000L)
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
                .hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `100자 초과 제목은 생성하지 않는다`() {
            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(title = "가".repeat(101))) }
                .hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `음수 최초 예산은 생성하지 않는다`() {
            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(initialBudget = -1L)) }
                .hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

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
                .hasErrorCode(ErrorCode.GROUP_NOT_FOUND)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `모임 구성원이 아니면 저장하지 않는다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest()) }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `OWNER가 아니면 저장하지 않는다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventService.createEvent(GROUP_ID, OWNER_ID, createRequest()) }
                .hasErrorCode(ErrorCode.NOT_GROUP_OWNER)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `종료 시각이 시작 시각보다 이르면 생성하지 않는다`() {
            val startAt = LocalDateTime.of(2026, 8, 28, 10, 0)

            assertThatThrownBy {
                eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(startAt = startAt, endAt = startAt))
            }.hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `시작 시각이 없으면 생성하지 않는다`() {
            assertThatThrownBy {
                eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(startAt = null))
            }.hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

            verify(exactly = 0) { eventRepository.save(any()) }
        }

        @Test
        fun `종료 시각이 없으면 생성하지 않는다`() {
            assertThatThrownBy {
                eventService.createEvent(GROUP_ID, OWNER_ID, createRequest(endAt = null))
            }.hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)

            verify(exactly = 0) { eventRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("행사 목록 조회")
    inner class GetEvents {

        @Test
        fun `상태가 없으면 논리 삭제된 행사를 제외한 모임의 전체 행사를 조회한다`() {
            every {
                eventRepository.findAllByGroupIdAndDeletedAtIsNullOrderByStartAtDesc(GROUP_ID)
            } returns listOf(event(EVENT_ID))

            val responses = eventService.getEvents(GROUP_ID, OWNER_ID)

            assertThat(responses.map { it.eventId }).containsExactly(EVENT_ID)
            verify { eventRepository.findAllByGroupIdAndDeletedAtIsNullOrderByStartAtDesc(GROUP_ID) }
        }

        @Test
        fun `상태가 있으면 논리 삭제된 행사를 제외하고 해당 상태의 행사만 조회한다`() {
            every {
                eventRepository.findAllByGroupIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(
                    GROUP_ID,
                    EventStatus.CLOSED,
                )
            } returns listOf(event(EVENT_ID, EventStatus.CLOSED))

            val responses = eventService.getEvents(GROUP_ID, OWNER_ID, EventStatus.CLOSED)

            assertThat(responses).allSatisfy { assertThat(it.status).isEqualTo(EventStatus.CLOSED) }
            verify {
                eventRepository.findAllByGroupIdAndStatusAndDeletedAtIsNullOrderByStartAtDesc(
                    GROUP_ID,
                    EventStatus.CLOSED,
                )
            }
        }

        @Test
        fun `목록 응답에는 승인 지출과 잔여 예산 필드가 없다`() {
            val fields = EventListResponse::class.members.map { it.name }

            assertThat(fields).doesNotContain("approvedSpending", "remainingBudget")
        }
    }

    @Nested
    @DisplayName("행사 상세 조회")
    inner class GetEvent {

        @Test
        fun `모임 구성원이 아니면 승인 지출 합계를 조회하지 않는다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
        }

        @Test
        fun `모임 구성원은 해당 모임의 행사 상세를 조회한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.groupId).isEqualTo(GROUP_ID)
            assertThat(response.additionalBudget).isZero()
            assertThat(response.totalBudget).isEqualTo(500_000L)
            assertThat(response.approvedSpending).isZero()
            assertThat(response.remainingBudget).isEqualTo(500_000L)
            verify { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) }
        }

        @Test
        fun `최초 예산과 추가 예산을 합산한 총예산을 조회한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
            every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 150_000L

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.initialBudget).isEqualTo(500_000L)
            assertThat(response.additionalBudget).isEqualTo(150_000L)
            assertThat(response.totalBudget).isEqualTo(650_000L)
            assertThat(response.approvedSpending).isZero()
            assertThat(response.remainingBudget).isEqualTo(650_000L)
        }

        @Test
        fun `최초 예산 추가 예산 승인 지출을 반영해 잔여 예산을 계산한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
            every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 150_000L
            every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 320_000L

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.totalBudget).isEqualTo(650_000L)
            assertThat(response.approvedSpending).isEqualTo(320_000L)
            assertThat(response.remainingBudget).isEqualTo(330_000L)
        }

        @Test
        fun `승인 지출이 총예산과 같으면 잔여 예산은 0원이다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
            every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 150_000L
            every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 650_000L

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.remainingBudget).isZero()
        }

        @Test
        fun `승인 지출이 총예산보다 크면 음수 잔여 예산을 그대로 반환한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
            every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 550_000L

            val response = eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID)

            assertThat(response.totalBudget).isEqualTo(500_000L)
            assertThat(response.remainingBudget).isEqualTo(-50_000L)
        }

        @Test
        fun `존재하지 않는 행사에서는 승인 지출 합계를 조회하지 않는다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
        }

        @Test
        fun `다른 모임 행사에서는 승인 지출 합계를 조회하지 않는다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
        }

        @Test
        fun `논리 삭제된 행사는 상세 조회 대상에서 제외한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { eventService.getEvent(GROUP_ID, EVENT_ID, OWNER_ID) }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verify { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) }
            verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
        }
    }

    private fun createRequest(
        title: String = "여름 MT",
        description: String? = null,
        startAt: LocalDateTime? = LocalDateTime.of(2026, 8, 28, 10, 0),
        endAt: LocalDateTime? = LocalDateTime.of(2026, 8, 30, 12, 0),
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
