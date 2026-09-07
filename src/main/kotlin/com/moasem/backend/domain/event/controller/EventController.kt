package com.moasem.backend.domain.event.controller

import com.moasem.backend.domain.event.dto.CreateBudgetAdditionRequest
import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.dto.CloseEventRequest
import com.moasem.backend.domain.event.dto.EventClosePreviewResponse
import com.moasem.backend.domain.event.dto.EventCloseResponse
import com.moasem.backend.domain.event.dto.EventDetailResponse
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.domain.event.service.BudgetAdditionService
import com.moasem.backend.domain.event.service.EventClosePreviewService
import com.moasem.backend.domain.event.service.EventCloseService
import com.moasem.backend.domain.event.service.EventDeletionService
import com.moasem.backend.domain.event.service.EventService
import com.moasem.backend.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 행사 생성·조회와 추가 예산 등록 API. 문서 정의는 [EventControllerDocs]에 둔다. */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/events")
class EventController(
    private val eventService: EventService,
    private val budgetAdditionService: BudgetAdditionService,
    private val eventDeletionService: EventDeletionService,
    private val eventClosePreviewService: EventClosePreviewService,
    private val eventCloseService: EventCloseService,
) : EventControllerDocs {

    @PostMapping
    override fun createEvent(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal currentUserId: Long,
        @Valid @RequestBody request: CreateEventRequest,
    ): ResponseEntity<ApiResponse<EventDetailResponse>> {
        val response = eventService.createEvent(groupId, currentUserId, request)
        return ResponseEntity
            .created(URI.create("/api/v1/groups/$groupId/events/${response.eventId}"))
            .body(ApiResponse.success("행사를 생성했습니다.", response))
    }

    @GetMapping
    override fun getEvents(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal currentUserId: Long,
        @RequestParam(required = false) status: EventStatus?,
    ): ApiResponse<List<EventListResponse>> =
        ApiResponse.success(eventService.getEvents(groupId, currentUserId, status))

    @GetMapping("/{eventId}")
    override fun getEvent(
        @PathVariable groupId: Long,
        @PathVariable eventId: Long,
        @AuthenticationPrincipal currentUserId: Long,
    ): ApiResponse<EventDetailResponse> =
        ApiResponse.success(eventService.getEvent(groupId, eventId, currentUserId))

    @PostMapping("/{eventId}/budget-additions")
    override fun addBudgetAddition(
        @PathVariable groupId: Long,
        @PathVariable eventId: Long,
        @AuthenticationPrincipal currentUserId: Long,
        @Valid @RequestBody request: CreateBudgetAdditionRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        budgetAdditionService.addBudgetAddition(groupId, eventId, currentUserId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok())
    }

    @DeleteMapping("/{eventId}")
    override fun deleteEvent(
        @PathVariable groupId: Long,
        @PathVariable eventId: Long,
        @AuthenticationPrincipal currentUserId: Long,
    ): ApiResponse<Unit> {
        eventDeletionService.deleteEvent(groupId, eventId, currentUserId)
        return ApiResponse.ok()
    }

    @PostMapping("/{eventId}/close-preview")
    override fun previewClose(
        @PathVariable groupId: Long,
        @PathVariable eventId: Long,
        @AuthenticationPrincipal currentUserId: Long,
        @Valid @RequestBody request: CloseEventRequest,
    ): ApiResponse<EventClosePreviewResponse> = ApiResponse.success(
        eventClosePreviewService.previewClose(groupId, eventId, currentUserId, request.participantCount),
    )

    @PostMapping("/{eventId}/close")
    override fun closeEvent(
        @PathVariable groupId: Long,
        @PathVariable eventId: Long,
        @AuthenticationPrincipal currentUserId: Long,
        @Valid @RequestBody request: CloseEventRequest,
    ): ApiResponse<EventCloseResponse> = ApiResponse.success(
        "행사를 마감했습니다.",
        eventCloseService.closeEvent(groupId, eventId, currentUserId, request.participantCount),
    )
}
