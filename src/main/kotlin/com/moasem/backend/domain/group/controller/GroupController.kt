package com.moasem.backend.domain.group.controller

import com.moasem.backend.domain.group.dto.response.CreateGroupResponse
import com.moasem.backend.domain.group.service.GroupService
import com.moasem.backend.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @RequestBody groupName: String
    ): ApiResponse<CreateGroupResponse> {
        val response = groupService.createGroup(userId, groupName)
        return ApiResponse.success(response)
    }

}