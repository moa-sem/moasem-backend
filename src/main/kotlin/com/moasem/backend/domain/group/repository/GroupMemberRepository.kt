package com.moasem.backend.domain.group.repository

import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    fun findByGroup(group: Group): MutableList<GroupMember>

    fun countByGroup(group: Group): Long

    @Query("""
        SELECT gm.group
        FROM GroupMember gm
        WHERE gm.user = :user AND gm.group.groupStatus = com.moasem.backend.domain.group.entity.GroupStatus.ACTIVE
    """)
    fun findActiveGroupByUser(@Param("user") user: User): List<Group>
}