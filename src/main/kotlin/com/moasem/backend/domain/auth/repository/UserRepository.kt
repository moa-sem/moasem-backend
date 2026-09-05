package com.moasem.backend.domain.auth.repository

import com.moasem.backend.domain.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByGoogleSub(googleSub: String): User?
}
