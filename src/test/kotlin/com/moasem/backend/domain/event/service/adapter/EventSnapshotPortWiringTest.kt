package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.report.service.port.EventSnapshotProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 결산 원자료를 실제 어댑터가 제공하는지 확인한다.
 *
 * 이 자리에는 예외를 던지는 스텁과 샘플을 돌려주는 로컬 저장소가 있었다. 스텁이 다시 이기면
 * 마감 보고서가 조용히 샘플 금액으로 만들어지고, 스냅샷은 불변이라 되돌릴 수 없다.
 * 실제로 어댑터가 없어 정산이 0원으로 계산되던 기간이 있었다(#63).
 */
@SpringBootTest
@ActiveProfiles("test")
class EventSnapshotPortWiringTest @Autowired constructor(
    private val eventSnapshotProvider: EventSnapshotProvider,
) {

    @Test
    @DisplayName("결산 원자료 port는 스텁이 아니라 실제 어댑터가 주입된다")
    fun snapshotUsesRealAdapter() {
        assertThat(eventSnapshotProvider).isInstanceOf(EventSnapshotAdapter::class.java)
    }

    /** 스텁이 살아 있었다면 없는 행사에도 UnsupportedOperationException을 던졌다. */
    @Test
    @DisplayName("없는 행사는 조회되지 않는다")
    fun missingEventIsNotFound() {
        assertThatThrownBy { eventSnapshotProvider.fetch(999_999L) }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}
