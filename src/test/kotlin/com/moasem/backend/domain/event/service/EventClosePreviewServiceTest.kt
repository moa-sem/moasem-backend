package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.hasErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventClosePreviewServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val budgetAdditionRepository = mockk<BudgetAdditionRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private val pendingSpendingCountProvider = mockk<PendingSpendingCountProvider>()
    private val approvedSpendingTotalProvider = mockk<ApprovedSpendingTotalProvider>()
    private lateinit var eventClosePreviewService: EventClosePreviewService

    @BeforeEach
    fun setUp() {
        eventClosePreviewService = EventClosePreviewService(
            eventRepository,
            budgetAdditionRepository,
            groupAccessProvider,
            pendingSpendingCountProvider,
            approvedSpendingTotalProvider,
        )
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
        every { pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID) } returns 0L
        every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 0L
        every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 0L
    }

    @Nested
    @DisplayName("행사 마감 미리보기")
    inner class PreviewClose {

        @Test
        fun `OWNER는 ACTIVE 행사의 마감 미리보기를 조회한다`() {
            val response = previewClose()

            assertThat(response.eventId).isEqualTo(EVENT_ID)
            assertThat(response.title).isEqualTo("여름 MT")
            assertThat(response.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(response.participantCount).isEqualTo(PARTICIPANT_COUNT)
            assertThat(response.pendingSpendingCount).isZero()
            assertThat(response.initialBudget).isEqualTo(INITIAL_BUDGET)
            assertThat(response.additionalBudget).isZero()
            assertThat(response.totalBudget).isEqualTo(INITIAL_BUDGET)
            assertThat(response.approvedSpending).isZero()
            assertThat(response.remainingBudget).isEqualTo(INITIAL_BUDGET)

            verifyOrder {
                groupAccessProvider.existsGroup(GROUP_ID)
                groupAccessProvider.isMember(GROUP_ID, OWNER_ID)
                groupAccessProvider.isOwner(GROUP_ID, OWNER_ID)
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID)
                pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID)
                budgetAdditionRepository.sumAmountByEventId(EVENT_ID)
                approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID)
            }
        }

        @Test
        fun `MEMBER는 마감 미리보기를 조회할 수 없다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.NOT_GROUP_OWNER)

            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(any(), any()) }
            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `비소속 사용자는 마감 미리보기를 조회할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            verify(exactly = 0) { groupAccessProvider.isOwner(any(), any()) }
            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(any(), any()) }
            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `존재하지 않는 모임의 마감 미리보기는 조회할 수 없다`() {
            every { groupAccessProvider.existsGroup(GROUP_ID) } returns false

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.GROUP_NOT_FOUND)

            verify(exactly = 0) { groupAccessProvider.isMember(any(), any()) }
            verify(exactly = 0) { groupAccessProvider.isOwner(any(), any()) }
            verify(exactly = 0) { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(any(), any()) }
            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `존재하지 않는 행사의 마감 미리보기는 조회할 수 없다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `다른 모임 행사의 마감 미리보기는 조회할 수 없다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `논리 삭제된 행사는 마감 미리보기 대상에서 제외한다`() {
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)

            verify { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) }
            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `CLOSED 행사는 마감 미리보기를 조회할 수 없다`() {
            every {
                eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID)
            } returns event(EVENT_ID, EventStatus.CLOSED)

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.EVENT_ALREADY_CLOSED)

            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `참여 인원이 0명 또는 음수이면 마감 미리보기를 조회할 수 없다`() {
            listOf(0, -1).forEach { invalidParticipantCount ->
                assertThatThrownBy { previewClose(participantCount = invalidParticipantCount) }
                    .hasErrorCode(ErrorCode.INVALID_INPUT_VALUE)
            }

            verifyPreviewPortsNotCalled()
        }

        @Test
        fun `참여 인원이 1명이면 마감 미리보기를 조회할 수 있다`() {
            val response = previewClose(participantCount = 1)

            assertThat(response.participantCount).isEqualTo(1)
        }

        @Test
        fun `PENDING 지출 신청이 있으면 마감 미리보기를 조회할 수 없다`() {
            every { pendingSpendingCountProvider.getPendingSpendingCount(EVENT_ID) } returns 2L

            assertThatThrownBy { previewClose() }
                .hasErrorCode(ErrorCode.EVENT_HAS_PENDING_SPENDING)

            verify(exactly = 0) { budgetAdditionRepository.sumAmountByEventId(any()) }
            verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
        }

        @Test
        fun `최초 예산만 있으면 총예산과 잔여 예산은 최초 예산과 같다`() {
            val response = previewClose()

            assertThat(response.initialBudget).isEqualTo(INITIAL_BUDGET)
            assertThat(response.additionalBudget).isZero()
            assertThat(response.totalBudget).isEqualTo(INITIAL_BUDGET)
            assertThat(response.approvedSpending).isZero()
            assertThat(response.remainingBudget).isEqualTo(INITIAL_BUDGET)
        }

        @Test
        fun `추가 예산과 승인 지출을 반영해 총예산과 잔여 예산을 계산한다`() {
            every { budgetAdditionRepository.sumAmountByEventId(EVENT_ID) } returns 150_000L
            every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 320_000L

            val response = previewClose()

            assertThat(response.initialBudget).isEqualTo(500_000L)
            assertThat(response.additionalBudget).isEqualTo(150_000L)
            assertThat(response.totalBudget).isEqualTo(650_000L)
            assertThat(response.approvedSpending).isEqualTo(320_000L)
            assertThat(response.remainingBudget).isEqualTo(330_000L)
        }

        @Test
        fun `승인 지출이 총예산보다 크면 음수 잔여 예산을 그대로 반환한다`() {
            every { approvedSpendingTotalProvider.getApprovedSpendingTotal(EVENT_ID) } returns 550_000L

            val response = previewClose()

            assertThat(response.totalBudget).isEqualTo(500_000L)
            assertThat(response.remainingBudget).isEqualTo(-50_000L)
        }

        @Test
        fun `미리보기는 행사 상태를 변경하거나 저장 또는 물리 삭제하지 않는다`() {
            val event = event(EVENT_ID)
            every { eventRepository.findByIdAndGroupIdAndDeletedAtIsNull(EVENT_ID, GROUP_ID) } returns event

            previewClose()

            assertThat(event.status).isEqualTo(EventStatus.ACTIVE)
            assertThat(event.deletedAt).isNull()
            verify(exactly = 0) { eventRepository.save(any<Event>()) }
            verify(exactly = 0) { eventRepository.delete(any<Event>()) }
            verify(exactly = 0) { eventRepository.deleteById(any()) }
        }
    }

    private fun previewClose(participantCount: Int = PARTICIPANT_COUNT) =
        eventClosePreviewService.previewClose(GROUP_ID, EVENT_ID, OWNER_ID, participantCount)

    private fun verifyPreviewPortsNotCalled() {
        verify(exactly = 0) { pendingSpendingCountProvider.getPendingSpendingCount(any()) }
        verify(exactly = 0) { budgetAdditionRepository.sumAmountByEventId(any()) }
        verify(exactly = 0) { approvedSpendingTotalProvider.getApprovedSpendingTotal(any()) }
    }

    private fun event(id: Long, status: EventStatus = EventStatus.ACTIVE): Event =
        Event.create(
            groupId = GROUP_ID,
            title = "여름 MT",
            description = null,
            startAt = LocalDateTime.of(2026, 8, 28, 10, 0),
            endAt = LocalDateTime.of(2026, 8, 30, 12, 0),
            initialBudget = INITIAL_BUDGET,
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
        private const val PARTICIPANT_COUNT = 3
        private const val INITIAL_BUDGET = 500_000L
    }
}
