package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingDetail
import com.moasem.backend.domain.event.service.port.ApprovedSpendingListProvider
import com.moasem.backend.domain.event.service.port.UserNameProvider
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 마감된 행사의 실제 데이터가 결산 원자료로 조립되는지 검증한다.
 *
 * 이 어댑터가 없던 동안 결산 보고서는 로컬 샘플로만 만들어졌다. 값이 틀려도 예외가 나지
 * 않는 종류의 고장이고, 스냅샷은 확정되면 불변이라 되돌릴 수 없다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventSnapshotAdapterTest @Autowired constructor(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
) {

    private val groupMembershipProvider = mockk<GroupMembershipProvider>()
    private val spendingListProvider = mockk<ApprovedSpendingListProvider>()
    private val userNameProvider = mockk<UserNameProvider>()

    private val adapter = EventSnapshotAdapter(
        eventRepository,
        budgetAdditionRepository,
        groupMembershipProvider,
        spendingListProvider,
        userNameProvider,
    )

    @BeforeEach
    fun setUp() {
        every { groupMembershipProvider.findGroupName(any()) } returns "백엔드 스터디"
        every { spendingListProvider.getApprovedSpendings(any()) } returns emptyList()
        every { userNameProvider.findNames(any()) } returns emptyMap()
    }

    @Test
    @DisplayName("마감된 행사의 원자료를 조립한다")
    fun assemblesClosedEvent() {
        val event = saveEvent(participantCount = 12)
        val eventId = eventId(event)
        saveAddition(eventId, amount = 150_000L, reason = "숙소 인원 추가", createdBy = OWNER_ID)
        every { spendingListProvider.getApprovedSpendings(eventId) } returns listOf(spendingDetail())
        every { userNameProvider.findNames(any()) } returns mapOf(OWNER_ID to "김소담", PAYER_ID to "윤석주")

        val data = adapter.fetch(eventId)

        assertThat(data.eventId).isEqualTo(eventId)
        assertThat(data.title).isEqualTo("여름 MT")
        assertThat(data.status).isEqualTo("CLOSED")
        assertThat(data.groupId).isEqualTo(GROUP_ID)
        assertThat(data.groupName).isEqualTo("백엔드 스터디")
        assertThat(data.participantCount).isEqualTo(12)
        assertThat(data.initialBudget).isEqualTo(800_000L)

        val addition = data.budgetAdditions.single()
        assertThat(addition.amount).isEqualTo(150_000L)
        assertThat(addition.reason).isEqualTo("숙소 인원 추가")
        assertThat(addition.addedBy).isEqualTo("김소담")
        assertThat(addition.addedAt).isNotNull()

        val spending = data.approvedSpendings.single()
        assertThat(spending.spendingId).isEqualTo(100L)
        assertThat(spending.description).isEqualTo("펜션 2박")
        assertThat(spending.amount).isEqualTo(420_000L)
        assertThat(spending.tag).isEqualTo("ACCOMMODATION")
        assertThat(spending.tagLabel).isEqualTo("숙박비")
        assertThat(spending.payerName).isEqualTo("윤석주")
        assertThat(spending.receiptKey).isEqualTo("spendings/1/evidence.jpg")
    }

    /** 마감 여부 판단은 report가 한다. 어댑터가 막으면 같은 규칙이 두 곳에 생긴다. */
    @Test
    @DisplayName("마감되지 않은 행사도 상태를 그대로 전달한다")
    fun passesStatusThrough() {
        val event = saveEvent(close = false)

        assertThat(adapter.fetch(eventId(event)).status).isEqualTo("ACTIVE")
    }

    @Test
    @DisplayName("없는 행사와 삭제된 행사는 조회되지 않는다")
    fun missingOrDeletedEventIsNotFound() {
        val deleted = saveEvent().also { it.delete() }
        eventRepository.flush()

        assertThatThrownBy { adapter.fetch(MISSING_EVENT_ID) }
            .isInstanceOf(NoSuchElementException::class.java)
        assertThatThrownBy { adapter.fetch(eventId(deleted)) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    /** 지출 수만큼 쿼리가 나가면 결산 한 건에 조회가 수십 번 나간다. */
    @Test
    @DisplayName("결제자와 예산 등록자의 이름을 한 번에 조회한다")
    fun looksUpNamesInOneCall() {
        val event = saveEvent()
        val eventId = eventId(event)
        saveAddition(eventId, createdBy = OWNER_ID)
        every { spendingListProvider.getApprovedSpendings(eventId) } returns listOf(
            spendingDetail(spendingId = 100L, applicantUserId = PAYER_ID),
            spendingDetail(spendingId = 101L, applicantUserId = OTHER_PAYER_ID),
        )
        val requested = slot<Collection<Long>>()
        every { userNameProvider.findNames(capture(requested)) } returns emptyMap()

        adapter.fetch(eventId)

        assertThat(requested.captured).containsExactlyInAnyOrder(OWNER_ID, PAYER_ID, OTHER_PAYER_ID)
    }

    /** 탈퇴한 사용자의 지출이 남아 있을 수 있다. 그 한 건 때문에 결산 전체가 멈추면 안 된다. */
    @Test
    @DisplayName("이름을 찾을 수 없는 사용자는 대체 표기로 남는다")
    fun unknownUserFallsBackToPlaceholder() {
        val event = saveEvent()
        val eventId = eventId(event)
        saveAddition(eventId, createdBy = OWNER_ID)
        every { spendingListProvider.getApprovedSpendings(eventId) } returns listOf(spendingDetail())

        val data = adapter.fetch(eventId)

        assertThat(data.budgetAdditions.single().addedBy).isEqualTo("탈퇴한 사용자")
        assertThat(data.approvedSpendings.single().payerName).isEqualTo("탈퇴한 사용자")
    }

    /** 라벨이 "기타"뿐이면 무슨 지출이었는지 보고서에 남지 않는다. */
    @Test
    @DisplayName("기타 태그의 상세 내용은 사유에 붙는다")
    fun otherDetailIsAppendedToDescription() {
        val event = saveEvent()
        val eventId = eventId(event)
        every { spendingListProvider.getApprovedSpendings(eventId) } returns listOf(
            spendingDetail(tag = "OTHER", tagLabel = "기타", description = "잡비", otherDetail = "구급약 구입"),
        )

        assertThat(adapter.fetch(eventId).approvedSpendings.single().description)
            .isEqualTo("잡비 (구급약 구입)")
    }

    /** 지출은 날짜까지만 기록한다. 스냅샷은 시각을 요구하므로 자정으로 맞춘다. */
    @Test
    @DisplayName("지출일은 자정 기준 시각으로 바뀐다")
    fun spentOnBecomesMidnight() {
        val event = saveEvent()
        val eventId = eventId(event)
        every { spendingListProvider.getApprovedSpendings(eventId) } returns listOf(
            spendingDetail(spentOn = LocalDate.of(2026, 8, 25)),
        )

        assertThat(adapter.fetch(eventId).approvedSpendings.single().spentAt)
            .isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0))
    }

    /** 이름 없는 보고서가 확정 저장되면 스냅샷은 불변이라 되돌릴 수 없다. */
    @Test
    @DisplayName("모임 이름을 찾을 수 없으면 실패한다")
    fun missingGroupNameFails() {
        val event = saveEvent()
        every { groupMembershipProvider.findGroupName(GROUP_ID) } returns null

        assertThatThrownBy { adapter.fetch(eventId(event)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun saveEvent(participantCount: Int = 8, close: Boolean = true): Event {
        val event = Event.create(
            groupId = GROUP_ID,
            title = "여름 MT",
            description = null,
            startAt = BASE_TIME,
            endAt = BASE_TIME.plusDays(2),
            initialBudget = 800_000L,
        )
        if (close) event.close(participantCount)
        return eventRepository.saveAndFlush(event)
    }

    private fun saveAddition(
        eventId: Long,
        amount: Long = 150_000L,
        reason: String = "숙소 인원 추가",
        createdBy: Long = OWNER_ID,
    ): BudgetAddition = budgetAdditionRepository.saveAndFlush(
        BudgetAddition.create(eventId = eventId, amount = amount, reason = reason, createdBy = createdBy),
    )

    private fun spendingDetail(
        spendingId: Long = 100L,
        applicantUserId: Long = PAYER_ID,
        description: String = "펜션 2박",
        tag: String = "ACCOMMODATION",
        tagLabel: String = "숙박비",
        otherDetail: String? = null,
        spentOn: LocalDate = LocalDate.of(2026, 8, 24),
    ) = ApprovedSpendingDetail(
        spendingId = spendingId,
        applicantUserId = applicantUserId,
        amount = 420_000L,
        spentOn = spentOn,
        tag = tag,
        tagLabel = tagLabel,
        description = description,
        otherDetail = otherDetail,
        evidenceStorageKey = "spendings/1/evidence.jpg",
    )

    private fun eventId(event: Event): Long = checkNotNull(event.id)

    companion object {
        private const val GROUP_ID = 10L
        private const val OWNER_ID = 30L
        private const val PAYER_ID = 40L
        private const val OTHER_PAYER_ID = 41L
        private const val MISSING_EVENT_ID = 999_999L
        private val BASE_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 24, 10, 0)
    }
}
