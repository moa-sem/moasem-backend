package com.moasem.backend.domain.group.entity

import com.moasem.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import java.time.LocalDateTime

data class Group(
    @Column(name = "group_host_id")
    val groupHostId: Long,

    @Column(name = "group_name")
    var groupName: String,

    @Column(name = "join_code")
    val joinCode: String,

    @Column(name = "group_status")
    val groupStatus: GroupStatus,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime,

): BaseEntity() {

}
