package com.moasem.backend.domain.event.service.adapter

import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** event 도메인의 모임 권한 조회 계약을 실제 group 데이터에 연결한다. */
@Component
@Transactional(readOnly = true)
class GroupAccessAdapter(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
) : GroupAccessProvider {

    override fun existsGroup(groupId: Long): Boolean = findActiveGroup(groupId) != null

    override fun isMember(groupId: Long, userId: Long): Boolean {
        val group = findActiveGroup(groupId) ?: return false
        val user = userRepository.findById(userId).orElse(null) ?: return false
        return groupMemberRepository.existsByGroupAndUser(group, user)
    }

    override fun isOwner(groupId: Long, userId: Long): Boolean =
        findActiveGroup(groupId)?.groupHostId == userId

    private fun findActiveGroup(groupId: Long): Group? {
        if (groupId <= 0) return null
        return groupRepository.findById(groupId).orElse(null)
            ?.takeIf { it.groupStatus == GroupStatus.ACTIVE && it.deletedAt == null }
    }
}
