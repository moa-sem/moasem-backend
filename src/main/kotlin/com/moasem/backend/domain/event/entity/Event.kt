package com.moasem.backend.domain.event.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "events")
class Event protected constructor(
    @Column(name = "group_id", nullable = false, updatable = false)
    val groupId: Long,
    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    val title: String,
    @Column(name = "description", columnDefinition = "text")
    val description: String?,
    @Column(name = "start_at", nullable = false)
    val startAt: LocalDateTime,
    @Column(name = "end_at", nullable = false)
    val endAt: LocalDateTime,
    @Column(name = "initial_budget", nullable = false)
    val initialBudget: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: EventStatus = EventStatus.ACTIVE
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    val isDeleted: Boolean
        get() = deletedAt != null

    fun delete(deletedAt: LocalDateTime = LocalDateTime.now()) {
        check(!isDeleted) { "이미 삭제된 행사입니다." }
        this.deletedAt = deletedAt
    }

    companion object {
        const val TITLE_MAX_LENGTH = 100

        fun create(
            groupId: Long,
            title: String,
            description: String?,
            startAt: LocalDateTime,
            endAt: LocalDateTime,
            initialBudget: Long,
        ): Event {
            require(groupId > 0) { "모임 ID는 양수여야 합니다." }
            require(title.isNotBlank()) { "행사 제목은 비어 있을 수 없습니다." }
            require(title.length <= TITLE_MAX_LENGTH) { "행사 제목은 ${TITLE_MAX_LENGTH}자 이하여야 합니다." }
            require(startAt.isBefore(endAt)) { "행사 종료 시각은 시작 시각보다 늦어야 합니다." }
            require(initialBudget >= 0) { "최초 예산은 0원 이상이어야 합니다." }

            return Event(
                groupId = groupId,
                title = title,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                startAt = startAt,
                endAt = endAt,
                initialBudget = initialBudget,
            )
        }
    }
}
