package com.moasem.backend.domain.event.dto

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CloseEventRequestTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `참여 인원 1명은 유효하다`() {
        val violations = validator.validate(CloseEventRequest(participantCount = 1))

        assertThat(violations).isEmpty()
    }

    @Test
    fun `참여 인원 양수는 유효하다`() {
        val violations = validator.validate(CloseEventRequest(participantCount = 3))

        assertThat(violations).isEmpty()
    }

    @Test
    fun `참여 인원 0명은 거부한다`() {
        assertParticipantCountViolation(0)
    }

    @Test
    fun `참여 인원 음수는 거부한다`() {
        assertParticipantCountViolation(-1)
    }

    private fun assertParticipantCountViolation(participantCount: Int) {
        val violations = validator.validate(CloseEventRequest(participantCount))

        assertThat(violations).hasSize(1)
        val violation = violations.single()
        assertThat(violation.propertyPath.toString()).isEqualTo("participantCount")
        assertThat(violation.message).isEqualTo("행사 참여 인원은 1명 이상이어야 합니다.")
    }
}
