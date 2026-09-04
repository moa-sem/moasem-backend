package com.moasem.backend.domain.auth.service

import com.moasem.backend.domain.auth.dto.GoogleTokenInfo
import com.moasem.backend.domain.auth.dto.request.RefreshTokenRequest
import com.moasem.backend.domain.auth.dto.response.TokenResponse
import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.global.security.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class GoogleAuthService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${moasem.oauth2.google.client-id}")
    private val googleClientId: String,
    @Value("\${moasem.jwt.refresh-token-expiration-ms}")
    private val refreshTokenExpirationMs: Long,
) {
    val restClient = RestClient.create()

    fun login(idToken: String): TokenResponse {
        val tokenInfo = verifyGoogleToken(idToken)

        val user = userRepository.findByGoogleSub(tokenInfo.sub)
            ?: userRepository.save(
                User(
                    googleSub = tokenInfo.sub,
                    email = tokenInfo.email,
                    name = tokenInfo.name,
                    profileImageUrl = tokenInfo.profileImageUrl
                )
            )

        val accessToken = jwtProvider.createAccessToken(user.id!!)
        val refreshToken = jwtProvider.createRefreshToken(user.id!!)
        redisTemplate.opsForValue().set(
            "refresh:${user.id}",
            refreshToken,
            Duration.ofMillis(refreshTokenExpirationMs)
        )

        return TokenResponse(accessToken, refreshToken)
    }

    fun refresh(refreshTokenRequest: RefreshTokenRequest): TokenResponse {
        if (!jwtProvider.validateToken(refreshTokenRequest.refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 refresh token 입니다.")
        }
        val userId = jwtProvider.extractUserId(refreshTokenRequest.refreshToken)
        val saved = redisTemplate.opsForValue().get("refresh:$userId")
        if (saved != refreshTokenRequest.refreshToken) {
            throw IllegalArgumentException("유효하지 않은 refresh token 입니다.")
        }
        val accessToken = jwtProvider.createAccessToken(userId)

        return TokenResponse(accessToken, refreshTokenRequest.refreshToken)
    }

    private fun verifyGoogleToken(idToken: String): GoogleTokenInfo {
        val tokenInfo = restClient.get()
            .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
            .retrieve()
            .body(GoogleTokenInfo::class.java)
            ?: throw IllegalArgumentException("유효하지 않은 Google 토큰입니다.")

        require(tokenInfo.aud == googleClientId) { "토큰의 클라이언트 ID가 일치하지 않습니다." }
        return tokenInfo
    }
}
