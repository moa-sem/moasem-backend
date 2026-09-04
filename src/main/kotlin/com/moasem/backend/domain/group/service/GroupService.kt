package com.moasem.backend.domain.group.service

import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.group.dto.response.CreateGroupResponse
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import com.moasem.backend.domain.group.entity.GroupRole
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GroupService(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {
    @Transactional
    fun createGroup(userId: Long, groupName: String): CreateGroupResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("존재하지 않는 유저입니다.") }

        val joinCode = generateUniqueJoinCode()

        val group = groupRepository.save(
            Group(
                groupHostId = userId,
                groupName = groupName,
                joinCode = joinCode,
                groupStatus = GroupStatus.ACTIVE,
            )
        )

        groupMemberRepository.save(
            GroupMember(group = group, user = user, role = GroupRole.OWNER)
        )

        return CreateGroupResponse(
            groupHostId = userId,
            groupId = group.id!!,
            groupName = groupName,
            joinCode = joinCode,
        )
    }

    private fun generateUniqueJoinCode(): String {
        var code: String
        do {
            code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        } while (groupRepository.existsByJoinCode(code))
        return code
    }
}
