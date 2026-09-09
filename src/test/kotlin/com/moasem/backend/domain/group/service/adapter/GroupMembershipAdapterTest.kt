package com.moasem.backend.domain.group.service.adapter

import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import com.moasem.backend.domain.group.entity.GroupRole
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

/**
 * 보고서 접근 권한과 결산 스냅샷의 모임 정보가 실제 group 데이터에서 나오는지 검증한다.
 *
 * 이 어댑터가 없던 동안 스텁이 모든 사용자를 구성원으로 돌려줬다. 권한 검사는 빠져도
 * 예외가 나지 않는 종류의 고장이라, 여기서 막지 못하면 아무도 모른다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GroupMembershipAdapter::class)
class GroupMembershipAdapterTest @Autowired constructor(
    private val adapter: GroupMembershipAdapter,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
) {

    @Test
    @DisplayName("모임에 속한 사용자는 구성원이다")
    fun memberBelongsToGroup() {
        val user = saveUser("김소담")
        val group = saveGroup()
        saveMember(group, user, GroupRole.MEMBER)

        assertThat(adapter.isMember(groupId(group), userId(user))).isTrue()
    }

    @Test
    @DisplayName("모임에 속하지 않은 사용자는 구성원이 아니다")
    fun outsiderIsNotMember() {
        val outsider = saveUser("윤석주")
        val group = saveGroup()

        assertThat(adapter.isMember(groupId(group), userId(outsider))).isFalse()
    }

    @Test
    @DisplayName("비활성·삭제된 모임에는 구성원이 없다")
    fun inactiveOrDeletedGroupHasNoMember() {
        val user = saveUser("이도현")
        val inactive = saveGroup(status = GroupStatus.INACTIVE)
        val deleted = saveGroup(deletedAt = LocalDateTime.of(2026, 9, 1, 12, 0))
        saveMember(inactive, user, GroupRole.MEMBER)
        saveMember(deleted, user, GroupRole.MEMBER)

        assertThat(adapter.isMember(groupId(inactive), userId(user))).isFalse()
        assertThat(adapter.isMember(groupId(deleted), userId(user))).isFalse()
    }

    @Test
    @DisplayName("없는 모임·잘못된 ID는 구성원이 아니다")
    fun unknownGroupHasNoMember() {
        val user = saveUser("박서진")

        assertThat(adapter.isMember(MISSING_GROUP_ID, userId(user))).isFalse()
        assertThat(adapter.isMember(0L, userId(user))).isFalse()
        assertThat(adapter.isMember(1L, 0L)).isFalse()
    }

    @Test
    @DisplayName("groupHostId와 같은 사용자가 모임장이다")
    fun hostIsOwner() {
        val owner = saveUser("김소담")
        val member = saveUser("윤석주")
        val group = saveGroup(hostId = userId(owner))
        saveMember(group, owner, GroupRole.OWNER)
        saveMember(group, member, GroupRole.MEMBER)

        assertThat(adapter.isOwner(groupId(group), userId(owner))).isTrue()
        assertThat(adapter.isOwner(groupId(group), userId(member))).isFalse()
    }

    @Test
    @DisplayName("비활성·삭제된 모임과 없는 모임에는 모임장이 없다")
    fun inactiveOrMissingGroupHasNoOwner() {
        val owner = saveUser("김소담")
        val inactive = saveGroup(hostId = userId(owner), status = GroupStatus.INACTIVE)
        val deleted = saveGroup(hostId = userId(owner), deletedAt = LocalDateTime.of(2026, 9, 1, 12, 0))

        assertThat(adapter.isOwner(groupId(inactive), userId(owner))).isFalse()
        assertThat(adapter.isOwner(groupId(deleted), userId(owner))).isFalse()
        assertThat(adapter.isOwner(MISSING_GROUP_ID, userId(owner))).isFalse()
        assertThat(adapter.isOwner(0L, userId(owner))).isFalse()
    }

    @Test
    @DisplayName("모임 이름을 조회한다")
    fun findsGroupName() {
        val group = saveGroup(name = "백엔드 스터디")

        assertThat(adapter.findGroupName(groupId(group))).isEqualTo("백엔드 스터디")
    }

    /** 결산 보고서는 마감 시점의 기록이다. 모임이 없어져도 이름은 남아야 한다. */
    @Test
    @DisplayName("삭제된 모임의 이름도 조회된다")
    fun findsNameOfDeletedGroup() {
        val group = saveGroup(name = "지난 모임", deletedAt = LocalDateTime.of(2026, 9, 1, 12, 0))

        assertThat(adapter.findGroupName(groupId(group))).isEqualTo("지난 모임")
    }

    @Test
    @DisplayName("없는 모임의 이름은 null이다")
    fun nameOfUnknownGroupIsNull() {
        assertThat(adapter.findGroupName(MISSING_GROUP_ID)).isNull()
    }

    @Test
    @DisplayName("구성원 목록은 이름과 모임장 여부를 함께 준다")
    fun listsMembersWithNameAndRole() {
        val owner = saveUser("김소담")
        val member = saveUser("윤석주")
        val group = saveGroup(hostId = userId(owner))
        saveMember(group, owner, GroupRole.OWNER)
        saveMember(group, member, GroupRole.MEMBER)

        val members = adapter.findMembers(groupId(group))

        assertThat(members).hasSize(2)
        assertThat(members).extracting("name", "isOwner")
            .containsExactlyInAnyOrder(tuple("김소담", true), tuple("윤석주", false))
        assertThat(members.map { it.userId }).containsExactlyInAnyOrder(userId(owner), userId(member))
    }

    @Test
    @DisplayName("다른 모임의 구성원은 섞이지 않는다")
    fun membersAreScopedToGroup() {
        val user = saveUser("이도현")
        val group = saveGroup()
        val otherGroup = saveGroup()
        saveMember(otherGroup, user, GroupRole.MEMBER)

        assertThat(adapter.findMembers(groupId(group))).isEmpty()
    }

    @Test
    @DisplayName("없는 모임의 구성원 목록은 빈 목록이다")
    fun membersOfUnknownGroupIsEmpty() {
        assertThat(adapter.findMembers(MISSING_GROUP_ID)).isEmpty()
    }

    private fun saveGroup(
        name: String = "테스트 모임",
        hostId: Long = HOST_ID,
        status: GroupStatus = GroupStatus.ACTIVE,
        deletedAt: LocalDateTime? = null,
    ): Group = groupRepository.save(
        Group(
            groupHostId = hostId,
            groupName = name,
            joinCode = UUID.randomUUID().toString().take(8),
            groupStatus = status,
            deletedAt = deletedAt,
        ),
    )

    private fun saveUser(name: String): User = userRepository.save(
        User(
            googleSub = "google-${UUID.randomUUID()}",
            email = "${UUID.randomUUID()}@example.com",
            name = name,
            profileImageUrl = null,
        ),
    )

    private fun saveMember(group: Group, user: User, role: GroupRole): GroupMember =
        groupMemberRepository.save(GroupMember(group, user, role))

    private fun groupId(group: Group): Long = checkNotNull(group.id)

    private fun userId(user: User): Long = checkNotNull(user.id)

    companion object {
        private const val HOST_ID = 999L
        private const val MISSING_GROUP_ID = 999_999L
    }
}
