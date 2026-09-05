package com.moasem.backend.domain.group.repository

import com.moasem.backend.domain.group.entity.Group
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository : JpaRepository<Group, Long> {
    fun existsByJoinCode(joinCode: String): Boolean
    fun findByJoinCode(joinCode: String): Group?
}