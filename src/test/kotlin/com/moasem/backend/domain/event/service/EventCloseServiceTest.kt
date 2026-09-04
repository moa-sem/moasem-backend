package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.ReportGenerationRequester
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.hasErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

class EventCloseServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private val pendingSpendingCountProvider = mockk<PendingSpendingCountProvider>()
    private val reportGenerationRequester = mockk<ReportGenerationRequester>()
    private lateinit var eventCloseService: EventCloseService

    @BeforeEach
    fun setUp() {
        eventCloseService = EventCloseService(
            eventRepository,
            groupAccessProvider,
            pendingSpendingCountProvider,
            reportGenerationRequester,
        )
        TransactionSynchronizationManager.initSynchronization()
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every {
            eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
        } returns event(EVENT_ID)
        every { pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID) } returns 0L
        every { reportGenerationRequester.requestReportGeneration(EVENT_ID) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Nested
    @DisplayName("행사 마감 확정")
    inner class CloseEvent {

        @Test
        fun `OWNER는 ACTIVE 행사를 마감한다`() {
            val response = closeEvent()

            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.status).isEqualTo(EventStatus.CLOSED)
            assertThat(response.participantCount).isEqualTo(PARTICIPANT_COUNT)
            assertThat(response.closedAt).isNotNull()
            verifyOrder {
                groupAccessProvider.existsGroup(GROUP_ID)
                groupAccessProvider.isMember(GROUP_ID, OWNER_ID)
                groupAccessProvider.isOwner(GROUP_ID, OWNER_ID)
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
                pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID)
            }
        }

        @Test
        fun `마감 성공 시 상태 참여 인원 마감 시각을 Event에 기록한다`() {
            val event = event(EVENT_ID)
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns event

            closeEvent()

            assertThat(event.status).isEqualTo(EventStatus.CLOSED)
            assertThat(event.participantCount).isEqualTo(PARTICIPANT_COUNT)
            assertThat(event.closedAt).isNotNull()
            verify(exactly = 0) { eventRepository.save(any<Event>()) }
        }

        @Test
        fun `MEMBER는 행사를 마감할 수 없다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.NOT_GROUP_OWNER)

            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(any(), any()) }
            verifyClosePortsNotCalled()
        }

        @Test
        fun `비소속 사용자는 행사를 마감할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            verify(exactly = 0) { groupAccessProvider.isOwner(any(), any()) }
            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(any(), any()) }
            verifyClosePortsNotCalled()
        }

        @Test
        fun `존재하지 않는 모임의 행사는 마감할 수 없다`() {
            every { groupAccessProvider.existsGroup(GROUP_ID) } returns false

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.GROUP_NOT_FOUND)

            verify(exactly = 0) { groupAccessProvider.isMember(any(), any()) }
            verify(exactly = 0) { groupAccessProvider.isOwner(any(), any()) }
            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(any(), any()) }
            verifyClosePortsNotCalled()
        }

        @Test
        fun `존재하지 않는 행사는 마감할 수 없다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns null

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verifyClosePortsNotCalled()
        }

        @Test
        fun `다른 모임에 속한 행사는 마감할 수 없다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns null

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verifyClosePortsNotCalled()
        }

        @Test
        fun `논리 삭제된 행사는 마감 대상에서 제외한다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns null

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verify { eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID) }
            verifyClosePortsNotCalled()
        }

        @Test
        fun `이미 CLOSED인 행사는 중복 마감할 수 없다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns closedEvent(EVENT_ID)

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.EVENT_ALREADY_CLOSED)

            verifyClosePortsNotCalled()
        }

        @Test
        fun `참여 인원이 0명 또는 음수이면 행사를 마감할 수 없다`() {
            val event = event(EVENT_ID)
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns event

            listOf(0, -1).forEach { invalidParticipantCount ->
                assertThatThrownBy { closeEvent(participantCount = invalidParticipantCount) }
                    .hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)
            }

            assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(event.participantCount).isNull()
            assertThat(event.closedAt).isNull()
            verifyClosePortsNotCalled()
        }

        @Test
        fun `PENDING 지출 신청이 있으면 행사를 마감할 수 없다`() {
            val event = event(EVENT_ID)
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns event
            every { pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID) } returns 1L

            assertThatThrownBy { closeEvent() }
                .hasErrorCode(ErrorCode.EVENT_HAS_PENDING_SPENDING)

            assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(event.participantCount).isNull()
            assertThat(event.closedAt).isNull()
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
            verify(exactly = 0) { reportGenerationRequester.requestReportGeneration(any()) }
        }

        @Test
        fun `정상 마감 시에만 보고서 생성 요청을 커밋 이후 실행한다`() {
            closeEvent()

            verify(exactly = 0) { reportGenerationRequester.requestReportGeneration(any()) }
            val synchronization = TransactionSynchronizationManager.getSynchronizations().single()

            synchronization.afterCommit()

            verify(exactly = 1) { reportGenerationRequester.requestReportGeneration(EVENT_ID) }
        }

        @Test
        fun `보고서 생성 요청 실패는 완료된 행사 마감을 되돌리지 않는다`() {
            val event = event(EVENT_ID)
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNullForUpdate(EVENT_ID, GROUP_ID)
            } returns event
            every {
                reportGenerationRequester.requestReportGeneration(EVENT_ID)
            } throws IllegalStateException("보고서 생성 실패")

            closeEvent()
            val synchronization = TransactionSynchronizationManager.getSynchronizations().single()

            assertThatCode { synchronization.afterCommit() }.doesNotThrowAnyException()
            assertThat(event.status).isEqualTo(EventStatus.CLOSED)
            assertThat(event.participantCount).isEqualTo(PARTICIPANT_COUNT)
            assertThat(event.closedAt).isNotNull()
        }
    }

    private fun closeEvent(participantCount: Int = PARTICIPANT_COUNT) =
        eventCloseService.closeEvent(GROUP_ID, EVENT_ID, OWNER_ID, participantCount)

    private fun verifyClosePortsNotCalled() {
        verify(exactly = 0) { pendingSpendingCountProvider.getPendingSpendingCount(any()) }
        verify(exactly = 0) { reportGenerationRequester.requestReportGeneration(any()) }
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
    }

    private fun event(id: Long): Event = Event.create(
        groupId = GROUP_ID,
        title = "여름 MT",
        description = null,
        startAt = LocalDateTime.of(2026, 8, 28, 10, 0),
        endAt = LocalDateTime.of(2026, 8, 30, 12, 0),
        initialBudget = 500_000L,
    ).also { assignId(it, id) }

    private fun closedEvent(id: Long): Event = event(id).also { it.close(PARTICIPANT_COUNT) }

    private fun assignId(event: Event, id: Long) {
        Event::class.java.getDeclaredField("id").apply {
            isAccessible = true
            set(event, id)
        }
    }

    companion object {
        private const val GROUP_ID = 1L
        private const val OWNER_ID = 10L
        private const val EVENT_ID = 100L
        private const val PARTICIPANT_COUNT = 3
    }
}
