package com.moasem.backend.domain.spending.service.adapter

import com.moasem.backend.domain.event.service.adapter.EventAccessAdapter
import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 실제 어댑터가 [com.moasem.backend.global.stub.LocalPortStubs]의 스텁을 이기는지 확인한다.
 *
 * 스텁은 `@ConditionalOnMissingBean`으로 걸려 있는데, 이 조건은 자동 구성 밖에서는 빈 정의가
 * 등록되는 순서에 좌우된다. 순서가 뒤집혀 스텁이 이기면 승인 지출 합계가 항상 0이 되고,
 * 그 값이 그대로 행사 마감 스냅샷에 확정 저장된다. 스냅샷은 불변이라 되돌릴 수 없다.
 *
 * 예외가 나지 않고 금액만 틀리는 종류의 고장이라, 이 테스트가 없으면 아무도 모른다.
 * 실제로 이 어댑터들이 없어 정산이 0원으로 계산되던 기간이 있었다(#63).
 */
@SpringBootTest
@ActiveProfiles("test")
class SpendingPortWiringTest @Autowired constructor(
    private val approvedSpendingTotalProvider: ApprovedSpendingTotalProvider,
    private val pendingSpendingCountProvider: PendingSpendingCountProvider,
    private val spendingHistoryProvider: SpendingHistoryProvider,
    private val eventAccessProvider: EventAccessProvider,
) {

    @Test
    @DisplayName("지출 집계 port는 스텁이 아니라 실제 어댑터가 주입된다")
    fun spendingAggregatesUseRealAdapter() {
        assertThat(approvedSpendingTotalProvider).isInstanceOf(SpendingQueryAdapter::class.java)
        assertThat(pendingSpendingCountProvider).isInstanceOf(SpendingQueryAdapter::class.java)
        assertThat(spendingHistoryProvider).isInstanceOf(SpendingQueryAdapter::class.java)
    }

    @Test
    @DisplayName("행사 조회 port는 실제 어댑터가 주입된다")
    fun eventAccessUsesRealAdapter() {
        assertThat(eventAccessProvider).isInstanceOf(EventAccessAdapter::class.java)
    }

    /** 스텁이 살아 있었다면 없는 행사에도 값을 돌려준다. 실제 어댑터는 null을 준다. */
    @Test
    @DisplayName("없는 행사는 조회되지 않는다")
    fun missingEventIsNotFound() {
        assertThat(eventAccessProvider.findAccess(999_999L)).isNull()
    }
}
