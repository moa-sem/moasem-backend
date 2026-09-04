package com.moasem.backend.domain.auth.controller

import com.moasem.backend.domain.auth.dto.request.GoogleLoginRequest
import com.moasem.backend.domain.auth.dto.request.RefreshTokenRequest
import com.moasem.backend.domain.auth.dto.response.TokenResponse
import com.moasem.backend.domain.auth.service.GoogleAuthService
import com.moasem.backend.global.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleAuthService: GoogleAuthService
) {

    @PostMapping("/google")
    fun googleLogin(
        @RequestBody googleLoginRequest: GoogleLoginRequest
    ): ApiResponse<TokenResponse> {
        val tokenResponse = googleAuthService.login(googleLoginRequest.idToken)
        return ApiResponse.success(tokenResponse)
    }

    @PostMapping("/refresh")
    fun refreshToken(
        @RequestBody refreshTokenRequest: RefreshTokenRequest
    ): ApiResponse<TokenResponse> {
        val refreshToken = googleAuthService.refresh(refreshTokenRequest)
        return ApiResponse.success(refreshToken)
    }

}