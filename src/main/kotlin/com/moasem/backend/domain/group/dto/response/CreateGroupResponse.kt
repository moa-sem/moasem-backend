package com.moasem.backend.domain.group.dto.response

data class CreateGroupResponse(
    val groupHostId: Long,
    val groupId: Long,
    val groupName: String,
    val joinCode: String,
)
