package com.moasem.backend.domain.event.service

import com.moasem.backend.domain.event.dto.CreateBudgetAdditionRequest
import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
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

class BudgetAdditionServiceTest {

    private val budgetAdditionRepository = mockk<BudgetAdditionRepository>()
    private val eventRepository = mockk<EventRepository>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private lateinit var budgetAdditionService: BudgetAdditionService

    @BeforeEach
    fun setUp() {
        budgetAdditionService = BudgetAdditionService(budgetAdditionRepository, eventRepository, groupAccessProvider)
        every { groupAccessProvider.existsGroup(GROUP_ID) } returns true
        every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns event(EVENT_ID)
    }

    @Nested
    @DisplayName("추가 예산 등록")
    inner class AddBudgetAddition {

        @Test
        fun `OWNER는 추가 예산의 금액 사유 등록자를 저장한다`() {
            val savedAddition = slot<BudgetAddition>()
            every { budgetAdditionRepository.save(capture(savedAddition)) } answers { savedAddition.captured }

            budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request())

            assertThat(savedAddition.captured.eventId).isEqualTo(EVENT_ID)
            assertThat(savedAddition.captured.amount).isEqualTo(100_000L)
            assertThat(savedAddition.captured.reason).isEqualTo("참가 인원 증가")
            assertThat(savedAddition.captured.createdBy).isEqualTo(OWNER_ID)
        }

        @Test
        fun `사유의 앞뒤 공백을 제거해 저장한다`() {
            val savedAddition = slot<BudgetAddition>()
            every { budgetAdditionRepository.save(capture(savedAddition)) } answers { savedAddition.captured }

            budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request(reason = "  참가 인원 증가  "))

            assertThat(savedAddition.captured.reason).isEqualTo("참가 인원 증가")
        }

        @Test
        fun `MEMBER는 추가 예산을 등록할 수 없다`() {
            every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임장만")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `비소속 사용자는 추가 예산을 등록할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OWNER_ID) } returns false

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임 구성원만")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 모임에는 추가 예산을 등록할 수 없다`() {
            every { groupAccessProvider.existsGroup(GROUP_ID) } returns false

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임을 찾을 수 없습니다")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 행사에는 추가 예산을 등록할 수 없다`() {
            every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("행사를 찾을 수 없습니다")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `다른 모임에 속한 행사에는 추가 예산을 등록할 수 없다`() {
            every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns null

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(NoSuchElementException::class.java)

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `CLOSED 행사에는 추가 예산을 등록할 수 없다`() {
            every { eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID) } returns event(EVENT_ID, EventStatus.CLOSED)

            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("진행 중인 행사")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `0원과 음수 금액은 등록할 수 없다`() {
            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request(amount = 0L)) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request(amount = -1L)) }
                .isInstanceOf(IllegalArgumentException::class.java)

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }

        @Test
        fun `공백 사유는 등록할 수 없다`() {
            assertThatThrownBy { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, OWNER_ID, request(reason = "   ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("사유")

            verify(exactly = 0) { budgetAdditionRepository.save(any()) }
        }
    }

    private fun request(amount: Long = 100_000L, reason: String = "참가 인원 증가") =
        CreateBudgetAdditionRequest(amount = amount, reason = reason)

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
