package com.moasem.backend.domain.group.controller

import com.moasem.backend.domain.group.dto.request.CreateGroupRequest
import com.moasem.backend.domain.group.dto.request.EnterGroupRequest
import com.moasem.backend.domain.group.dto.response.CreateGroupResponse
import com.moasem.backend.domain.group.dto.response.EnterGroupResponse
import com.moasem.backend.domain.group.dto.response.GetGroupListResponse
import com.moasem.backend.domain.group.service.GroupService
import com.moasem.backend.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/group")
class GroupController(
    private val groupService: GroupService
) {
    @PostMapping
    fun createGroup(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateGroupRequest
    ): ApiResponse<CreateGroupResponse> {
        val response = groupService.createGroup(userId, request.groupName)
        return ApiResponse.success(response)
    }

    @PostMapping("/enter")
    fun enterGroup(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: EnterGroupRequest
    ): ApiResponse<EnterGroupResponse> {
        val response = groupService.enterGroup(userId, request.joinCode)
        return ApiResponse.success(response)
    }

    @GetMapping
    fun groupList(
        @AuthenticationPrincipal userId: Long
    ): ApiResponse<List<GetGroupListResponse>> {
        val response = groupService.getGroupList(userId)
        return ApiResponse.success(response)
    }
}
