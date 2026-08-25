package com.moasem.backend.domain.report.service.port

import java.time.LocalDateTime

/**
 * 결산에 필요한 행사·지출 원자료를 제공한다.
 *
 * report 도메인이 event·spending·group 도메인에 직접 의존하지 않도록 격리하는 경계다.
 * 해당 도메인 구현이 끝나면 이 인터페이스를 구현하는 어댑터만 추가하면 되고,
 * 결산 계산 로직은 바뀌지 않는다.
 */
interface EventSnapshotProvider {

    /**
     * 행사 ID로 결산 원자료를 조회한다.
     *
     * @throws NoSuchElementException 행사가 존재하지 않는 경우
     */
    fun fetch(eventId: Long): EventSnapshotData
}

/**
 * 결산 계산에 필요한 행사 정보 일체.
 *
 * [approvedSpendings]에는 **승인된 지출만** 담긴다. "승인된 지출만 예산에 반영한다"는
 * 규칙은 제공자 쪽에서 보장하며, report는 상태를 다시 필터링하지 않는다.
 * 규칙이 두 곳으로 흩어지면 한쪽만 고쳐졌을 때 금액이 어긋나기 때문이다.
 */
data class EventSnapshotData(
    val eventId: Long,
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: String,
    val groupId: Long,
    val groupName: String,
    /**
     * 행사 참여 인원. event 도메인에서 마감 시 입력받는 값이나 아직 확정되지 않아 nullable이다.
     *
     * 스냅샷은 JSONB에 불변으로 저장되므로 나중에 필드를 추가해도 기존 보고서에는 값이 없다.
     * 그래서 쓰지 않더라도 자리를 미리 잡아 둔다.
     */
    val participantCount: Int?,
    val initialBudget: Long,
    val budgetAdditions: List<BudgetAdditionData>,
    val approvedSpendings: List<ApprovedSpendingData>,
)

data class BudgetAdditionData(
    val amount: Long,
    val reason: String,
    val addedAt: LocalDateTime,
)

data class ApprovedSpendingData(
    val spendingId: Long,
    val description: String,
    val amount: Long,
    val tag: String,
    val payerName: String,
    val spentAt: LocalDateTime,
    val receiptUrl: String?,
)
