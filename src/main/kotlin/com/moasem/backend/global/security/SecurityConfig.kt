package com.moasem.backend.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import tools.jackson.databind.ObjectMapper
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * 보안 설정.
 *
 * 경로별 인증 요구 여부와 STATELESS 설정, 인증 실패 응답을 담당한다.
 * 사용자 식별은 [JwtAuthenticationFilter]가 토큰에서 꺼내 주체로 심는다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val environment: Environment,
    objectMapper: ObjectMapper,
) {

    private val securityExceptionHandler = SecurityExceptionHandler(objectMapper)

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
                    .requestMatchers(*publicPaths()).permitAll()
                    .anyRequest().authenticated()
            }
            // 필터 단계에서 걸린 요청은 컨트롤러에 도달하지 않아 GlobalExceptionHandler가
            // 잡지 못한다. 여기서 공통 응답 형태로 만들어 준다.
            .exceptionHandling {
                it.authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * 로컬에서만 개발용 경로를 연다.
     *
     * 개발용 dev 경로는 보고서 파일과 증빙을 브라우저 주소창으로 직접 여는 데 쓴다.
     * 인증을 걸면 다운로드 URL을 붙여넣어 확인하는 흐름이 막힌다.
     *
     * 해당 컨트롤러가 `@Profile("local")` 이라 운영에는 존재하지 않지만, 보안 설정에서도
     * 열지 않는다. 나중에 프로파일 없는 개발용 컨트롤러가 추가돼도 운영에 노출되지 않는다.
     */
    private fun publicPaths(): Array<String> =
        if (environment.matchesProfiles(LOCAL_PROFILE)) PUBLIC_PATHS + LOCAL_ONLY_PATHS else PUBLIC_PATHS

    companion object {
        private const val LOCAL_PROFILE = "local"

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

        private val LOCAL_ONLY_PATHS = arrayOf("/api/v1/dev/**")
    }
}
