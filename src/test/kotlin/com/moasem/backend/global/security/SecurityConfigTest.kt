package com.moasem.backend.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * 인증 우회가 다시 열리지 않는지 확인한다.
 *
 * 인증 수단이 없던 시기에 업무 API 전체를 열어 두었고, 그 상태로 배포되면 모든 API가
 * 토큰 없이 호출된다. 되돌아가도 다른 테스트는 전부 통과하는 종류라 문자열로 고정한다.
 *
 * 설정 파일을 읽어 검사하므로 컨텍스트를 띄우지 않는다.
 */
class SecurityConfigTest {

    private val source = Files.readString(
        Path.of("src/main/kotlin/com/moasem/backend/global/security/SecurityConfig.kt"),
    )

    @Test
    @DisplayName("업무 API 전체를 여는 경로가 없다")
    fun noBlanketApiBypass() {
        val publicPaths = publicPathsBlock()

        assertThat(publicPaths)
            .withFailMessage("PUBLIC_PATHS에 업무 API를 통째로 여는 경로가 있습니다.%n%s", publicPaths)
            .doesNotContain("\"/api/v1/**\"")
    }

    @Test
    @DisplayName("공개 경로는 로그인·문서·헬스 체크로 제한된다")
    fun onlyKnownPublicPaths() {
        val paths = Regex("\"(/[^\"]*)\"").findAll(publicPathsBlock())
            .map { it.groupValues[1] }
            .toSet()

        // 새 경로를 열 때는 왜 인증 없이 열어야 하는지 확인하고 이 목록에 추가한다.
        assertThat(paths).containsExactlyInAnyOrder(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/actuator/health",
        )
    }

    @Test
    @DisplayName("개발용 경로는 로컬 프로파일에서만 열린다")
    fun devPathsAreLocalOnly() {
        // 브라우저 주소창으로 직접 여는 로컬 파일 경로라 인증을 걸 수 없다.
        // 운영에서도 열리면 그 경로에 무엇이 붙든 무방비가 된다.
        assertThat(source).contains("LOCAL_ONLY_PATHS")
        assertThat(source).contains("matchesProfiles(LOCAL_PROFILE)")
        assertThat(publicPathsBlock()).doesNotContain("/api/v1/dev")
    }

    /** PUBLIC_PATHS 배열 선언부만 잘라낸다. 로컬 전용 목록과 섞이지 않게 한다. */
    private fun publicPathsBlock(): String {
        val start = source.indexOf("private val PUBLIC_PATHS")
        check(start >= 0) { "PUBLIC_PATHS 선언을 찾을 수 없습니다." }
        val end = source.indexOf(")", source.indexOf("arrayOf(", start))
        return source.substring(start, end)
    }
}
