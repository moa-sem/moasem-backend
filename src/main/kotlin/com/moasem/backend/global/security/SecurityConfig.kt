package com.moasem.backend.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 최소 보안 설정.
 *
 * 현재는 경로별 인증 요구 여부와 STATELESS 설정만 담당한다.
 * JWT 인증 필터 연결은 auth 도메인에서 별도로 추가한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // REST API + STATELESS 이므로 CSRF 토큰을 쓰지 않는다.
            .csrf { it.disable() }
            // 기본 폼 로그인 페이지가 모든 요청을 가로채는 것을 막는다.
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { registry ->
                registry
                    .requestMatchers(*PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()
    }

    companion object {
        private val PUBLIC_PATHS = arrayOf(
            // Swagger
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // 로그인/토큰 재발급
            "/api/v1/auth/**",
            // 헬스 체크
            "/actuator/health",
        )
    }
}
