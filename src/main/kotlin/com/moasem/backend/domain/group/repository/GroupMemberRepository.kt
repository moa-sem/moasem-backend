package com.moasem.backend.domain.group.repository

import com.moasem.backend.domain.group.entity.GroupMember
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMemberRepository : JpaRepository<GroupMember, Long>
