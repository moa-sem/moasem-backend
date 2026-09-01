package com.moasem.backend.domain.spending.service

import com.moasem.backend.domain.spending.dto.RejectSpendingRequest
import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.repository.SpendingRepository
import com.moasem.backend.domain.spending.service.port.EventAccess
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpendingApprovalServiceTest {

    private val spendingRepository = mockk<SpendingRepository>()
    private val eventAccessProvider = mockk<EventAccessProvider>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private lateinit var approvalService: SpendingApprovalService

    @BeforeEach
    fun setUp() {
        approvalService = SpendingApprovalService(spendingRepository, eventAccessProvider, groupAccessProvider)
        every { eventAccessProvider.findAccess(EVENT_ID) } returns EventAccess(EVENT_ID, GROUP_ID, true)
        every { groupAccessProvider.isOwner(GROUP_ID, OWNER_ID) } returns true
        every { groupAccessProvider.isOwner(GROUP_ID, MEMBER_ID) } returns false
    }

    @Nested
    @DisplayName("지출 승인")
    inner class Approve {

        @Test
        fun `모임장이 승인하면 처리자와 처리 시각이 기록된다`() {
            val spending = givenPendingSpending()

            val response = approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID)

            assertThat(spending.status).isEqualTo(SpendingStatus.APPROVED)
            assertThat(spending.processedByUserId).isEqualTo(OWNER_ID)
            assertThat(spending.processedAt).isNotNull()
            assertThat(response.status).isEqualTo(SpendingStatus.APPROVED)
            assertThat(response.processedByUserId).isEqualTo(OWNER_ID)
        }

        @Test
        fun `잔여 예산을 초과하는 금액도 승인한다`() {
            val spending = givenPendingSpending(amount = 999_999_999L)

            approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID)

            assertThat(spending.status).isEqualTo(SpendingStatus.APPROVED)
        }

        @Test
        fun `모임장이 아닌 구성원은 승인할 수 없다`() {
            assertThatThrownBy { approvalService.approve(EVENT_ID, SPENDING_ID, MEMBER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임장만")

            verify(exactly = 0) { spendingRepository.findWithLockByIdAndEventId(any(), any()) }
        }

        @Test
        fun `다른 행사에 속한 지출은 처리할 수 없다`() {
            every { spendingRepository.findWithLockByIdAndEventId(SPENDING_ID, EVENT_ID) } returns null

            assertThatThrownBy { approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("지출을 찾을 수 없습니다")
        }

        @Test
        fun `없는 행사의 지출은 처리할 수 없다`() {
            every { eventAccessProvider.findAccess(EVENT_ID) } returns null

            assertThatThrownBy { approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("행사를 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("지출 반려")
    inner class Reject {

        @Test
        fun `모임장이 반려하면 사유가 함께 기록된다`() {
            val spending = givenPendingSpending()

            val response = approvalService.reject(EVENT_ID, SPENDING_ID, OWNER_ID, rejectRequest())

            assertThat(spending.status).isEqualTo(SpendingStatus.REJECTED)
            assertThat(spending.rejectionReason).isEqualTo("증빙 이미지가 흐립니다.")
            assertThat(response.rejectionReason).isEqualTo("증빙 이미지가 흐립니다.")
            assertThat(response.processedByUserId).isEqualTo(OWNER_ID)
        }

        @Test
        fun `반려 사유가 공백뿐이면 반려할 수 없다`() {
            val spending = givenPendingSpending()

            assertThatThrownBy { approvalService.reject(EVENT_ID, SPENDING_ID, OWNER_ID, rejectRequest("   ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("반려 사유")

            assertThat(spending.status).isEqualTo(SpendingStatus.PENDING)
        }

        @Test
        fun `모임장이 아닌 구성원은 반려할 수 없다`() {
            assertThatThrownBy { approvalService.reject(EVENT_ID, SPENDING_ID, MEMBER_ID, rejectRequest()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("모임장만")
        }
    }

    @Nested
    @DisplayName("중복 처리 방지")
    inner class DuplicateProcessing {

        @Test
        fun `처리 대상은 락을 걸고 조회한다`() {
            givenPendingSpending()

            approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID)

            verify(exactly = 1) { spendingRepository.findWithLockByIdAndEventId(SPENDING_ID, EVENT_ID) }
        }

        @Test
        fun `이미 승인된 건은 다시 승인할 수 없다`() {
            givenPendingSpending()
            approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID)

            assertThatThrownBy { approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")
        }

        @Test
        fun `승인된 건을 뒤늦게 반려할 수 없다`() {
            val spending = givenPendingSpending()
            approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID)

            assertThatThrownBy { approvalService.reject(EVENT_ID, SPENDING_ID, OWNER_ID, rejectRequest()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")

            assertThat(spending.status).isEqualTo(SpendingStatus.APPROVED)
            assertThat(spending.rejectionReason).isNull()
        }

        @Test
        fun `반려된 건을 뒤늦게 승인할 수 없다`() {
            val spending = givenPendingSpending()
            approvalService.reject(EVENT_ID, SPENDING_ID, OWNER_ID, rejectRequest())

            assertThatThrownBy { approvalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")

            assertThat(spending.status).isEqualTo(SpendingStatus.REJECTED)
        }
    }

    /** 락 조회가 돌려줄 PENDING 지출을 준비한다. 같은 인스턴스를 계속 돌려주므로 재처리 시도를 재현할 수 있다. */
    private fun givenPendingSpending(amount: Long = 15_000L): Spending {
        val spending = Spending.create(
            eventId = EVENT_ID,
            applicantUserId = MEMBER_ID,
            amount = amount,
            spentOn = LocalDate.of(2026, 8, 20),
            reason = "1일차 점심 식사",
            tag = SpendingTag.MEAL,
            otherDetail = null,
            evidence = Spending.Evidence(
                EvidenceType.RECEIPT,
                "spendings/$EVENT_ID/$MEMBER_ID/evidence.jpg",
                "image/jpeg",
                204_800L,
            ),
        ).also {
            val idField = Spending::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(it, SPENDING_ID)
        }
        every { spendingRepository.findWithLockByIdAndEventId(SPENDING_ID, EVENT_ID) } returns spending
        return spending
    }

    private fun rejectRequest(reason: String = "증빙 이미지가 흐립니다.") = RejectSpendingRequest(reason)

    companion object {
        private const val GROUP_ID = 1L
        private const val EVENT_ID = 100L
        private const val MEMBER_ID = 10L
        private const val OWNER_ID = 30L
        private const val SPENDING_ID = 500L
    }
}
