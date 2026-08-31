package com.moasem.backend

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 애플리케이션이 실제로 기동되는지 확인한다.
 *
 * 다른 테스트는 fake를 직접 주입하거나 슬라이스(@WebMvcTest, @DataJpaTest)만 로드하기
 * 때문에, 빈 주입이 깨져도 전부 통과한다. 실제로 port 어댑터가 없어 기동이 불가능한
 * 상태에서도 테스트가 100% 초록이었고, 머지된 뒤에야 문제를 알게 되는 일이 반복됐다.
 *
 * 이 테스트는 전체 컨텍스트를 띄우므로 그런 누락을 PR 단계에서 잡는다.
 * 새 port를 추가하면 어댑터나 스텁도 함께 넣어야 이 테스트가 통과한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 로드된다")
    fun contextLoads() {
        // 컨텍스트 로딩 자체가 검증 대상이다. 빈이 하나라도 주입되지 않으면 여기 도달하지 못한다.
    }
}
