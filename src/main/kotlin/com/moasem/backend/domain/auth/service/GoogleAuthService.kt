package com.moasem.backend.domain.auth.service

import com.moasem.backend.domain.auth.dto.GoogleTokenInfo
import com.moasem.backend.domain.auth.dto.request.RefreshTokenRequest
import com.moasem.backend.domain.auth.dto.response.TokenResponse
import com.moasem.backend.domain.auth.entity.Member
import com.moasem.backend.domain.auth.repository.MemberRepository
import com.moasem.backend.global.security.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class GoogleAuthService (
    private val memberRepository: MemberRepository,
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

        val member = memberRepository.findByGoogleSub(tokenInfo.sub)
            ?: memberRepository.save(
                Member(
                    googleSub = tokenInfo.sub,
                    email = tokenInfo.email,
                    name = tokenInfo.name,
                    profileImageUrl = tokenInfo.profileImageUrl
                )
            )

        val accessToken = jwtProvider.createAccessToken(member.id!!)
        val refreshToken = jwtProvider.createRefreshToken(member.id!!)
        redisTemplate.opsForValue().set(
            "refresh:${member.id}",
            refreshToken,
            Duration.ofMillis(refreshTokenExpirationMs)
        )

        return TokenResponse (accessToken, refreshToken)
    }

    fun refresh(refreshTokenRequest: RefreshTokenRequest): TokenResponse {
        if (!jwtProvider.validateToken(refreshTokenRequest.refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 refresh token 입니다.")
        }
        val memberId = jwtProvider.extractMemberId(refreshTokenRequest.refreshToken)
        val saved = redisTemplate.opsForValue().get("refresh:$memberId")
        if (saved != refreshTokenRequest.refreshToken) {
            throw IllegalArgumentException("유효하지 않은 refresh token 입니다.")
        }
        val accessToken = jwtProvider.createAccessToken(memberId)

        return TokenResponse(accessToken, refreshTokenRequest.refreshToken)
    }

    private fun verifyGoogleToken(idToken: String): GoogleTokenInfo {
        val tokenInfo = restClient.get()
            .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
            .retrieve()
            .body(GoogleTokenInfo::class.java)
            ?: throw IllegalArgumentException("유효하지 않은 Google 토큰입니다.")

        require(tokenInfo.aud == googleClientId) {"토큰의 클라이언트 ID가 일치하지 않습니다."}
        return tokenInfo
    }

}