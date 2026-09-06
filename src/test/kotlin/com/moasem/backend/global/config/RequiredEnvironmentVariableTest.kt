package com.moasem.backend.global.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * 기본값 없는 환경변수가 배포 경로에서 빠지지 않았는지 확인한다.
 *
 * 같은 사고가 두 번 났다. `${VAR}` 를 기본값 없이 추가하면 그 변수가 없는 환경에서는
 * 애플리케이션이 기동조차 못 한다. 그런데 테스트는 `application-test.yml` 에 값이 있어
 * 전부 통과하고, 슬라이스 테스트도 컨텍스트 전체를 띄우지 않아 드러나지 않는다.
 * 머지된 뒤 각자 받아서 실행할 때, 또는 배포가 실패할 때 알게 된다.
 *
 * 여기서는 설정 파일을 문자열로 읽어 검사한다. 컨텍스트도 DB도 필요 없다.
 */
class RequiredEnvironmentVariableTest {

    private val applicationYml = read("src/main/resources/application.yml")
    private val prodCompose = read("docker-compose.prod.yml")

    @Test
    @DisplayName("기본값 없는 환경변수는 운영 compose가 모두 전달한다")
    fun prodComposeProvidesEveryRequiredVariable() {
        val missing = requiredVariables() - providedByProdCompose()

        assertThat(missing)
            .withFailMessage(
                """
                운영 compose가 넘겨주지 않는 필수 환경변수가 있습니다: %s

                이대로 배포하면 앱이 기동하지 못합니다. 둘 중 하나를 하세요.
                  - docker-compose.prod.yml 의 app.environment 에 추가하고 docs/DEPLOYMENT.md 갱신
                  - application.yml 에서 ${'$'}{VAR:기본값} 형태로 기본값 부여
                """.trimIndent(),
                missing,
            )
            .isEmpty()
    }

    @Test
    @DisplayName("토큰 서명 키는 로컬 기본값과 운영 값이 분리돼 있다")
    fun jwtSecretIsNotDefaultedGlobally() {
        // 최상단에 기본값을 두면 운영도 저장소에 공개된 문자열로 토큰에 서명하게 된다.
        // 로컬 프로파일에서만 기본값을 주고, 운영은 환경변수를 요구해야 한다.
        assertThat(requiredVariables())
            .describedAs("JWT_SECRET에 전역 기본값이 생기면 운영이 공개된 키로 서명하게 된다")
            .contains("JWT_SECRET")
    }

    /** `${VAR}` 중 `:` 로 기본값을 주지 않은 것들. 값이 없으면 기동에 실패한다. */
    private fun requiredVariables(): Set<String> =
        Regex("""\$\{([A-Z][A-Z0-9_]*)}""").findAll(applicationYml)
            .map { it.groupValues[1] }
            .toSet()

    /**
     * compose가 app 컨테이너에 넘기는 변수 이름.
     *
     * `SPRING_PROFILES_ACTIVE: prod` 처럼 값을 직접 적은 것과
     * `DB_URL: jdbc:...${DB_HOST}...` 처럼 조합해 넘기는 것 모두 전달로 본다.
     */
    private fun providedByProdCompose(): Set<String> =
        Regex("""^\s{6}([A-Z][A-Z0-9_]*):""", RegexOption.MULTILINE).findAll(prodCompose)
            .map { it.groupValues[1] }
            .toSet()

    private fun read(path: String): String {
        val file = Path.of(path)
        check(Files.exists(file)) { "설정 파일을 찾을 수 없습니다: $path" }
        return Files.readString(file)
    }
}
