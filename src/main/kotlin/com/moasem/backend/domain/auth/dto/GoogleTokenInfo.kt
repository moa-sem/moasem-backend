package com.moasem.backend.domain.auth.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GoogleTokenInfo(
    val sub: String,
    val email: String,
    val name: String,
    @JsonProperty("picture")
    val profileImageUrl: String?,
    val aud: String
)
