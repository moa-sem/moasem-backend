package com.moasem.backend.domain.auth.entity

import com.moasem.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(name = "google_sub", unique = true, nullable = false)
    val googleSub: String,

    @Column(name = "email")
    val email: String,

    @Column(name = "name")
    val name: String,

    @Column(name = "profile_image_url")
    val profileImageUrl: String?,
) : BaseEntity()
