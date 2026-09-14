package com.moasem.backend.domain.event.repository

import com.moasem.backend.domain.event.entity.BudgetAddition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BudgetAdditionRepositoryTest @Autowired constructor(
    private val budgetAdditionRepository: BudgetAdditionRepository,
) {

    @Test
    @DisplayName("여러 행사의 추가 예산을 행사별로 일괄 합산한다")
    fun sumAmountsByEventIds() {
        save(EVENT_ID, 100_000L)
        save(EVENT_ID, 50_000L)
        save(OTHER_EVENT_ID, 30_000L)
        budgetAdditionRepository.flush()

        val totals = budgetAdditionRepository
            .sumAmountsByEventIds(listOf(EVENT_ID, OTHER_EVENT_ID, EMPTY_EVENT_ID))
            .associate { it.eventId to it.totalAmount }

        assertThat(totals).containsExactlyInAnyOrderEntriesOf(
            mapOf(EVENT_ID to 150_000L, OTHER_EVENT_ID to 30_000L),
        )
        assertThat(totals).doesNotContainKey(EMPTY_EVENT_ID)
    }

    private fun save(eventId: Long, amount: Long) {
        budgetAdditionRepository.save(
            BudgetAddition.create(
                eventId = eventId,
                amount = amount,
                reason = "참가 인원 증가",
                createdBy = OWNER_ID,
            ),
        )
    }

    companion object {
        private const val EVENT_ID = 100L
        private const val OTHER_EVENT_ID = 200L
        private const val EMPTY_EVENT_ID = 300L
        private const val OWNER_ID = 10L
    }
}
