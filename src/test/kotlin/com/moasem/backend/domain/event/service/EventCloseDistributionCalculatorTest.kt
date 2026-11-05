package com.moasem.backend.domain.event.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EventCloseDistributionCalculatorTest {

    private val calculator = EventCloseDistributionCalculator()

    @Test
    @DisplayName("양수 잔액은 1인당 반환액을 내림하고 나머지를 반환한다")
    fun calculatesRefundWithRemainder() {
        val result = calculator.calculate(totalBudget = 350_000L, approvedSpending = 0L, participantCount = 3)

        assertThat(result.remainingBudget).isEqualTo(350_000L)
        assertThat(result.refundPerPerson).isEqualTo(116_666L)
        assertThat(result.refundRemainder).isEqualTo(2L)
        assertThat(result.additionalChargePerPerson).isZero()
        assertThat(result.overCollectedAmount).isZero()
    }

    @Test
    @DisplayName("잔액이 0원이면 반환액과 추가 부담액이 모두 0원이다")
    fun calculatesZeroBalance() {
        val result = calculator.calculate(totalBudget = 350_000L, approvedSpending = 350_000L, participantCount = 3)

        assertThat(result.remainingBudget).isZero()
        assertThat(result.refundPerPerson).isZero()
        assertThat(result.refundRemainder).isZero()
        assertThat(result.additionalChargePerPerson).isZero()
        assertThat(result.overCollectedAmount).isZero()
    }

    @Test
    @DisplayName("음수 잔액의 부족액은 1인당 추가 부담액을 올림하고 초과 징수액을 반환한다")
    fun calculatesAdditionalChargeWithOverCollection() {
        val result = calculator.calculate(totalBudget = 0L, approvedSpending = 100_000L, participantCount = 3)

        assertThat(result.remainingBudget).isEqualTo(-100_000L)
        assertThat(result.refundPerPerson).isZero()
        assertThat(result.refundRemainder).isZero()
        assertThat(result.additionalChargePerPerson).isEqualTo(33_334L)
        assertThat(result.overCollectedAmount).isEqualTo(2L)
    }

    @Test
    @DisplayName("부족액이 나누어떨어지면 초과 징수액이 0원이다")
    fun calculatesEvenAdditionalCharge() {
        val result = calculator.calculate(totalBudget = 0L, approvedSpending = 99_999L, participantCount = 3)

        assertThat(result.additionalChargePerPerson).isEqualTo(33_333L)
        assertThat(result.overCollectedAmount).isZero()
    }

    @Test
    @DisplayName("분배 인원이 0명이면 계산하지 않는다")
    fun rejectsZeroParticipants() {
        assertThatIllegalArgumentException()
            .isThrownBy { calculator.calculate(totalBudget = 100L, approvedSpending = 0L, participantCount = 0) }
    }
}
