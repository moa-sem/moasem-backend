package com.moasem.backend.domain.group.dto.response

data class GetGroupListResponse(
    val groupId: Long,
    val groupName: String,
    val groupMemberCount: Int,
    val isGroupHost: Boolean,
    val joinCode: String,
)
