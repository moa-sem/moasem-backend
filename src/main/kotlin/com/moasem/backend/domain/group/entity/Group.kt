package com.moasem.backend.domain.group.entity

import com.moasem.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "groups")
class Group(
    @Column(name = "group_host_id", nullable = false)
    val groupHostId: Long,

    @Column(name = "group_name", nullable = false)
    var groupName: String,

    @Column(name = "join_code", unique = true, nullable = false)
    val joinCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "group_status", nullable = false)
    val groupStatus: GroupStatus,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

) : BaseEntity()
