package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.event.entity.BudgetAddition
import com.moasem.backend.domain.event.entity.Event
import com.moasem.backend.domain.event.repository.BudgetAdditionRepository
import com.moasem.backend.domain.event.repository.EventRepository
import com.moasem.backend.domain.event.service.port.ApprovedSpendingDetail
import com.moasem.backend.domain.event.service.port.ApprovedSpendingListProvider
import com.moasem.backend.domain.event.service.port.UserNameProvider
import com.moasem.backend.domain.report.service.port.ApprovedSpendingData
import com.moasem.backend.domain.report.service.port.BudgetAdditionData
import com.moasem.backend.domain.report.service.port.EventSnapshotData
import com.moasem.backend.domain.report.service.port.EventSnapshotProvider
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * report 도메인이 요구하는 결산 원자료 경계의 구현.
 *
 * 행사를 소유한 event 도메인이 남의 port를 구현한다. 결산에 필요한 값이 네 도메인에 흩어져
 * 있어서, 행사를 가진 쪽이 나머지를 port로 불러 모으는 편이 조립 지점이 하나로 유지된다.
 *
 * - 행사·예산 추가: 직접 조회
 * - 모임 이름: [GroupMembershipProvider] (group)
 * - 승인 지출: [ApprovedSpendingListProvider] (spending)
 * - 사람 이름: [UserNameProvider] (auth)
 *
 * 계산은 하지 않는다. 합계·잔액·태그별 집계는 전부 report의
 * [com.moasem.backend.domain.report.service.ReportSnapshotCalculator]가 맡는다.
 * 금액을 두 곳에서 더하면 한쪽만 고쳐졌을 때 조용히 어긋난다.
 */
@Component
@Transactional(readOnly = true)
class EventSnapshotAdapter(
    private val eventRepository: EventRepository,
    private val budgetAdditionRepository: BudgetAdditionRepository,
    private val groupMembershipProvider: GroupMembershipProvider,
    private val approvedSpendingListProvider: ApprovedSpendingListProvider,
    private val userNameProvider: UserNameProvider,
) : EventSnapshotProvider {

    override fun fetch(eventId: Long): EventSnapshotData {
        val event = eventRepository.findById(eventId).orElse(null)?.takeIf { !it.isDeleted }
            ?: throw NoSuchElementException("행사를 찾을 수 없습니다. eventId=$eventId")

        val additions = budgetAdditionRepository.findAllByEventId(eventId)
        val spendings = approvedSpendingListProvider.getApprovedSpendings(eventId)
        // 결제자와 예산 등록자를 한 번에 모아 조회한다. 건별로 물어보면 지출 수만큼 쿼리가 나간다.
        val names = userNameProvider.findNames(
            additions.map { it.createdBy } + spendings.map { it.applicantUserId },
        )

        return EventSnapshotData(
            eventId = eventId,
            title = event.title,
            startAt = event.startAt,
            endAt = event.endAt,
            // 마감 여부 판단은 report가 한다. 여기서 막으면 같은 규칙이 두 곳에 생긴다.
            status = event.status.name,
            groupId = event.groupId,
            groupName = groupName(event),
            participantCount = event.participantCount,
            initialBudget = event.initialBudget,
            budgetAdditions = additions.map { toAdditionData(it, names) },
            approvedSpendings = spendings.map { toSpendingData(it, names) },
        )
    }

    /**
     * 모임 이름이 없으면 실패시킨다.
     *
     * 소프트 삭제만 쓰므로 정상 경로에서는 일어나지 않는다. 그래도 빈 문자열로 넘기지 않는 건
     * 스냅샷이 불변이기 때문이다. 이름 없는 보고서가 확정 저장되면 되돌릴 수 없다.
     */
    private fun groupName(event: Event): String =
        groupMembershipProvider.findGroupName(event.groupId)
            ?: throw IllegalStateException("모임을 찾을 수 없습니다. groupId=${event.groupId}")

    private fun toAdditionData(addition: BudgetAddition, names: Map<Long, String>) = BudgetAdditionData(
        amount = addition.amount,
        reason = addition.reason,
        addedBy = names[addition.createdBy] ?: UNKNOWN_USER_NAME,
        addedAt = checkNotNull(addition.createdAt) { "저장되지 않은 예산 추가입니다." },
    )

    private fun toSpendingData(spending: ApprovedSpendingDetail, names: Map<Long, String>) = ApprovedSpendingData(
        spendingId = spending.spendingId,
        description = describe(spending),
        amount = spending.amount,
        tag = spending.tag,
        tagLabel = spending.tagLabel,
        payerName = names[spending.applicantUserId] ?: UNKNOWN_USER_NAME,
        // 지출은 날짜까지만 기록한다. 시각은 없으므로 자정으로 맞춘다.
        spentAt = spending.spentOn.atStartOfDay(),
        receiptKey = spending.evidenceStorageKey,
    )

    /** 기타 태그는 라벨이 "기타"뿐이라, 사용자가 쓴 상세 내용을 사유에 붙여야 무슨 지출인지 남는다. */
    private fun describe(spending: ApprovedSpendingDetail): String =
        spending.otherDetail?.let { "${spending.description} ($it)" } ?: spending.description

    companion object {
        /** 탈퇴한 사용자의 지출이 남아 있을 수 있다. 그 한 건 때문에 결산 전체가 멈추지는 않는다. */
        private const val UNKNOWN_USER_NAME = "탈퇴한 사용자"
    }
}
