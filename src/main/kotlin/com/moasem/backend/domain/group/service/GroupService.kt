package com.moasem.backend.domain.group.service

import com.moasem.backend.domain.auth.repository.UserRepository
import com.moasem.backend.domain.group.dto.response.CreateGroupResponse
import com.moasem.backend.domain.group.dto.response.EnterGroupResponse
import com.moasem.backend.domain.group.dto.response.GetGroupListResponse
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import com.moasem.backend.domain.group.entity.GroupRole
import com.moasem.backend.domain.group.entity.GroupStatus
import com.moasem.backend.domain.group.repository.GroupMemberRepository
import com.moasem.backend.domain.group.repository.GroupRepository
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
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

    @Transactional
    fun enterGroup(userId: Long, joinCode: String): EnterGroupResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("존재하지 않는 유저입니다.") }
        val group = groupRepository.findByJoinCode(joinCode)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw BusinessException(ErrorCode.ALREADY_GROUP_MEMBER)
        }

        groupMemberRepository.save(
            GroupMember(group = group, user = user, role = GroupRole.MEMBER)
        )

        return EnterGroupResponse(groupId = group.id!!, groupName = group.groupName)
    }

    @Transactional(readOnly = true)
    fun getGroupList(userId: Long): List<GetGroupListResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("존재하지 않는 유저입니다.") }

        return groupMemberRepository.findActiveGroupByUser(user).map { group ->
            GetGroupListResponse(
                groupId = group.id!!,
                groupName = group.groupName,
                groupMemberCount = groupMemberRepository.countByGroup(group).toInt(),
                isGroupHost = group.groupHostId == userId,
                joinCode = group.joinCode,
            )
        }
    }

    private fun generateUniqueJoinCode(): String {
        var code: String
        do {
            code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        } while (groupRepository.existsByJoinCode(code))
        return code
    }
}
