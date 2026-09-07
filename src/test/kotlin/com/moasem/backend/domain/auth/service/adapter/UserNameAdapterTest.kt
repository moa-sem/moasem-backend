package com.moasem.backend.domain.auth.service.adapter

import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.auth.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * 결산 보고서의 사람 이름 칸이 실제 사용자 데이터에서 나오는지 검증한다.
 *
 * 없는 사용자를 예외로 다루지 않는 것이 핵심이다. 탈퇴한 사용자의 지출이 남아 있을 때
 * 결산 전체가 실패하면 안 된다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserNameAdapter::class)
class UserNameAdapterTest @Autowired constructor(
    private val adapter: UserNameAdapter,
    private val userRepository: UserRepository,
) {

    @Test
    @DisplayName("여러 사용자의 이름을 한 번에 조회한다")
    fun findsNamesInBatch() {
        val first = saveUser("김소담")
        val second = saveUser("윤석주")

        val names = adapter.findNames(listOf(userId(first), userId(second)))

        assertThat(names).containsOnly(
            entry(userId(first), "김소담"),
            entry(userId(second), "윤석주"),
        )
    }

    /** 탈퇴한 사용자의 지출이 남아 있을 수 있다. 결산 전체가 실패하면 안 된다. */
    @Test
    @DisplayName("없는 사용자는 결과에서 빠질 뿐 예외가 되지 않는다")
    fun missingUserIsOmitted() {
        val user = saveUser("이도현")

        val names = adapter.findNames(listOf(userId(user), MISSING_USER_ID))

        assertThat(names).containsOnlyKeys(userId(user))
    }

    @Test
    @DisplayName("빈 입력과 잘못된 ID는 빈 결과다")
    fun emptyInputReturnsEmptyMap() {
        assertThat(adapter.findNames(emptyList())).isEmpty()
        assertThat(adapter.findNames(listOf(0L, -1L))).isEmpty()
    }

    @Test
    @DisplayName("같은 ID가 여러 번 들어와도 한 번만 조회한다")
    fun duplicatedIdsAreCollapsed() {
        val user = saveUser("박서진")

        val names = adapter.findNames(listOf(userId(user), userId(user), userId(user)))

        assertThat(names).hasSize(1)
        assertThat(names[userId(user)]).isEqualTo("박서진")
    }

    private fun saveUser(name: String): User = userRepository.save(
        User(
            googleSub = "google-${UUID.randomUUID()}",
            email = "${UUID.randomUUID()}@example.com",
            name = name,
            profileImageUrl = null,
        ),
    )

    private fun userId(user: User): Long = checkNotNull(user.id)

    companion object {
        private const val MISSING_USER_ID = 999_999L
    }
}
