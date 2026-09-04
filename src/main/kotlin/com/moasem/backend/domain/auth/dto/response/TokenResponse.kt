package com.moasem.backend.domain.auth.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)