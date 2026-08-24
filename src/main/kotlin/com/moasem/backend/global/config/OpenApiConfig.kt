package com.moasem.backend.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val info = Info()
            .title("모아셈 API")
            .description("모임·동아리 공금 운영 서비스 API 문서")
            .version("v0.0.1")

        // Swagger UI 우측 상단 Authorize 버튼에서 JWT를 넣을 수 있게 한다.
        // 실제 토큰 발급은 auth 도메인 구현 후 동작한다.
        val securityScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name(AUTHORIZATION_HEADER)

        return OpenAPI()
            .info(info)
            .components(Components().addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme))
            .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
    }

    companion object {
        private const val SECURITY_SCHEME_NAME = "bearerAuth"
        private const val AUTHORIZATION_HEADER = "Authorization"
    }
}
