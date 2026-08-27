package com.moasem.backend.domain.event.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "budget_additions")
class BudgetAddition protected constructor(
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: Long,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    @Column(name = "reason", nullable = false, columnDefinition = "text", updatable = false)
    val reason: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null

    companion object {
        fun create(eventId: Long, amount: Long, reason: String, createdBy: Long): BudgetAddition {
            require(eventId > 0) { "행사 ID는 양수여야 합니다." }
            require(amount > 0) { "추가 예산 금액은 0원보다 커야 합니다." }
            require(createdBy > 0) { "등록자 ID는 양수여야 합니다." }

            val normalizedReason = reason.trim()
            require(normalizedReason.isNotEmpty()) { "추가 예산 사유는 비어 있을 수 없습니다." }

            return BudgetAddition(eventId, amount, normalizedReason, createdBy)
        }
    }
}
