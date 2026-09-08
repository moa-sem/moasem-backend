package com.moasem.backend.global.security

import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

/**
 * 보안 필터 단계에서 걸린 요청의 응답을 만든다.
 *
 * 이 단계는 컨트롤러 앞이라 `GlobalExceptionHandler` 가 잡지 못한다. 그대로 두면 Spring
 * 기본 동작으로 본문 없는 403이 나가는데, 프론트는 "인증이 없는 것"과 "권한이 없는 것"을
 * 구분하지 못하고 응답 형태도 다른 API와 달라진다.
 *
 * 별도 빈으로 등록하지 않고 [SecurityConfig]가 직접 만든다. 컴포넌트로 두면 SecurityConfig를
 * 가져다 쓰는 슬라이스 테스트마다 이 빈까지 함께 등록해야 한다.
 */
class SecurityExceptionHandler(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    /** 토큰이 없거나 유효하지 않다. 다시 로그인하면 해결되는 상황이므로 401이다. */
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = write(response, ErrorCode.UNAUTHORIZED)

    /** 인증은 됐지만 권한이 없다. 다시 로그인해도 달라지지 않으므로 403이다. */
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = write(response, ErrorCode.ACCESS_DENIED)

    private fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ApiResponse.error(errorCode, errorCode.message),
        )
    }
}
