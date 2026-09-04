package com.moasem.backend.global.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtProvider (
    @Value("\${moasem.jwt.secret}")
    private val secretKeyString: String,
    @Value("\${moasem.jwt.access-token-expiration-ms}")
    private val accessTokenValidityInMilliseconds: Long,
    @Value("\${moasem.jwt.refresh-token-expiration-ms}")
    private val refreshTokenValidityInMilliseconds: Long
) {
    private val key: Key = Keys.hmacShaKeyFor(secretKeyString.toByteArray())

    fun createAccessToken (memberId: Long): String {
        val now = Date()
        val validity = Date(now.time + accessTokenValidityInMilliseconds)

        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun createRefreshToken(memberId: Long): String {
        val now = Date()
        val validity = Date(now.time + refreshTokenValidityInMilliseconds)

        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(validity)
            .signWith(key)
            .compact()
    }

    fun extractMemberId(token: String): Long {
        return parseClaims(token).subject.toLong()
    }

    fun validateToken(token: String): Boolean {
        try {
            parseClaims(token)
            return true
        } catch (e: JwtException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser().verifyWith(key as SecretKey).build()
            .parseSignedClaims(token).payload
    }
}