package com.moasem.backend.domain.auth.repository

import com.moasem.backend.domain.auth.entity.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository: JpaRepository<Member, Long> {
    fun findByGoogleSub(googleSub: String): Member?
}