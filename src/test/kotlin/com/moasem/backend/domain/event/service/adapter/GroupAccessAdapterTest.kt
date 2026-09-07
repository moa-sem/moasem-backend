package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import com.moasem.backend.domain.group.entity.GroupRole
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GroupAccessAdapter::class)
class GroupAccessAdapterTest @Autowired constructor(
    private val adapter: GroupAccessAdapter,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
) {

    @Test
    @DisplayName("ACTIVE이고 삭제되지 않은 모임만 존재하는 모임이다")
    fun activeGroupExists() {
        val group = saveGroup()

        assertThat(adapter.existsGroup(checkNotNull(group.id))).isTrue()
    }

    @Test
    @DisplayName("INACTIVE 모임은 존재하지 않는 모임으로 처리한다")
    fun inactiveGroupDoesNotExist() {
        val group = saveGroup(status = GroupStatus.INACTIVE)

        assertThat(adapter.existsGroup(checkNotNull(group.id))).isFalse()
    }

    @Test
    @DisplayName("논리 삭제된 모임은 존재하지 않는 모임으로 처리한다")
    fun deletedGroupDoesNotExist() {
        val group = saveGroup(deletedAt = LocalDateTime.of(2026, 9, 1, 12, 0))

        assertThat(adapter.existsGroup(checkNotNull(group.id))).isFalse()
    }

    @Test
    @DisplayName("GroupMember가 있으면 모임 구성원이다")
    fun memberExists() {
        val user = saveUser("member")
        val group = saveGroup()
        groupMemberRepository.save(GroupMember(group, user, GroupRole.MEMBER))

        assertThat(adapter.isMember(checkNotNull(group.id), checkNotNull(user.id))).isTrue()
    }

    @Test
    @DisplayName("GroupMember가 없으면 모임 구성원이 아니다")
    fun memberDoesNotExist() {
        val user = saveUser("outsider")
        val group = saveGroup()

        assertThat(adapter.isMember(checkNotNull(group.id), checkNotNull(user.id))).isFalse()
    }

    @Test
    @DisplayName("groupHostId가 사용자 ID와 같으면 모임장이다")
    fun hostIsOwner() {
        val owner = saveUser("owner")
        val group = saveGroup(hostId = checkNotNull(owner.id))

        assertThat(adapter.isOwner(checkNotNull(group.id), checkNotNull(owner.id))).isTrue()
    }

    @Test
    @DisplayName("groupHostId와 다른 사용자는 모임장이 아니다")
    fun memberIsNotOwner() {
        val member = saveUser("member")
        val group = saveGroup(hostId = OTHER_USER_ID)

        assertThat(adapter.isOwner(checkNotNull(group.id), checkNotNull(member.id))).isFalse()
    }

    private fun saveGroup(
        hostId: Long = OTHER_USER_ID,
        status: GroupStatus = GroupStatus.ACTIVE,
        deletedAt: LocalDateTime? = null,
    ): Group = groupRepository.save(
        Group(
            groupHostId = hostId,
            groupName = "테스트 모임",
            joinCode = UUID.randomUUID().toString(),
            groupStatus = status,
            deletedAt = deletedAt,
        ),
    )

    private fun saveUser(name: String): User = userRepository.save(
        User(
            googleSub = "google-$name-${UUID.randomUUID()}",
            email = "$name@example.com",
            name = name,
            profileImageUrl = null,
        ),
    )

    companion object {
        private const val OTHER_USER_ID = 999L
    }
}
