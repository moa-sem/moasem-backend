package com.moasem.backend.domain.auth.service.adapter

import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.event.service.port.UserNameProvider
import com.moasem.backend.domain.spending.service.port.UserNameProvider as SpendingUserNameProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 이름 조회 경계의 구현. 사용자 데이터를 가진 auth 도메인이 남의 port를 구현한다.
 *
 * 호출하는 쪽은 [com.moasem.backend.domain.auth.entity.User]를 알지 못한다.
 * 이름 말고 이메일·구글 식별자 같은 값이 함께 새어 나가지 않는다.
 *
 * event와 spending이 같은 모양의 경계를 각자 두고 있어 둘 다 구현한다. 조회 규칙이
 * 한 곳에만 있으면 "없는 사용자를 어떻게 다루는가"가 도메인마다 갈리지 않는다.
 */
@Component
@Transactional(readOnly = true)
class UserNameAdapter(
    private val userRepository: UserRepository,
) : UserNameProvider, SpendingUserNameProvider {

    override fun findNames(userIds: Collection<Long>): Map<Long, String> {
        val ids = userIds.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return emptyMap()

        return userRepository.findAllById(ids).associate { checkNotNull(it.id) to it.name }
    }
}
