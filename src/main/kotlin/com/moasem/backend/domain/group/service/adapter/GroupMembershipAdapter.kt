package com.moasem.backend.domain.group.service.adapter

import com.moasem.backend.domain.group.entity.GroupRole
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import com.moasem.backend.domain.report.service.port.GroupMemberData
import com.moasem.backend.domain.report.service.port.GroupMembershipProvider
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 모임 조회 경계의 구현. 모임 데이터를 가진 group 도메인이 남의 port를 구현한다.
 *
 * 호출하는 쪽은 [com.moasem.backend.domain.group.entity.Group]도
 * [com.moasem.backend.domain.group.entity.GroupMember]도 알지 못한다.
 *
 * report의 조회 경계와 spending의 권한 경계를 한 클래스에 모았다. 둘 다 "이 모임의
 * 구성원인가"를 묻는 같은 질문이라, 나누면 비활성·삭제된 모임을 어떻게 볼지가
 * 두 곳으로 흩어진다.
 */
@Component
@Transactional(readOnly = true)
class GroupMembershipAdapter(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) : GroupMembershipProvider, GroupAccessProvider {

    /**
     * 권한 판단이므로 모임 상태까지 본다. 비활성·삭제된 모임에는 구성원이 없다고 본다.
     * [com.moasem.backend.domain.event.service.adapter.GroupAccessAdapter]와 같은 기준이다.
     */
    override fun isMember(groupId: Long, userId: Long): Boolean {
        if (groupId <= 0 || userId <= 0) return false
        if (findActiveGroupHostId(groupId) == null) return false

        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)
    }

    /**
     * 모임 상태를 따지지 않는다. 결산 보고서에 찍히는 표시용 이름이라,
     * 나중에 모임이 없어져도 그 행사가 어느 모임 것이었는지는 남아야 한다.
     */
    override fun findGroupName(groupId: Long): String? =
        groupRepository.findById(groupId).orElse(null)?.groupName

    /**
     * 모임장은 `groupHostId`로 판단한다.
     *
     * [com.moasem.backend.domain.group.entity.GroupMember.role]에도 OWNER가 있지만,
     * 기존 [com.moasem.backend.domain.event.service.adapter.GroupAccessAdapter]가 쓰는 기준을
     * 따른다. 두 기준이 갈리면 같은 사용자가 행사에서는 모임장이고 지출에서는 아니게 된다.
     */
    override fun isOwner(groupId: Long, userId: Long): Boolean {
        if (groupId <= 0 || userId <= 0) return false
        return findActiveGroupHostId(groupId) == userId
    }

    override fun findMembers(groupId: Long): List<GroupMemberData> =
        groupMemberRepository.findAllWithUserByGroupId(groupId).map {
            GroupMemberData(
                userId = checkNotNull(it.user.id) { "저장되지 않은 사용자입니다." },
                name = it.user.name,
                isOwner = it.role == GroupRole.OWNER,
            )
        }

    /** 살아 있는 모임이면 모임장 ID, 아니면 null. 존재 확인과 모임장 판별을 한 번에 한다. */
    private fun findActiveGroupHostId(groupId: Long): Long? =
        groupRepository.findById(groupId).orElse(null)
            ?.takeIf { it.groupStatus == GroupStatus.ACTIVE && it.deletedAt == null }
            ?.groupHostId
}
