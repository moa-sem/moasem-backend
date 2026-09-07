package com.moasem.backend.domain.event.controller

import com.moasem.backend.domain.event.dto.CreateBudgetAdditionRequest
import com.moasem.backend.domain.event.dto.CreateEventRequest
import com.moasem.backend.domain.event.dto.CloseEventRequest
import com.moasem.backend.domain.event.dto.EventClosePreviewResponse
import com.moasem.backend.domain.event.dto.EventCloseResponse
import com.moasem.backend.domain.event.dto.EventDetailResponse
import com.moasem.backend.domain.event.dto.EventListResponse
import com.moasem.backend.domain.event.entity.EventStatus
import com.moasem.backend.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse

/** Swagger 어노테이션을 분리해 실제 Controller에는 매핑과 서비스 위임만 남긴다. */
@Tag(name = "Event", description = "행사 생성·조회·예산 관리")
interface EventControllerDocs {

    @Operation(summary = "행사 생성", description = "모임장이 ACTIVE 상태의 새 행사를 생성한다.")
    @ApiResponses(
        SwaggerResponse(responseCode = "201", description = "생성 성공. Location 헤더에 행사 상세 경로가 담긴다"),
        SwaggerResponse(responseCode = "400", description = "입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(responseCode = "403", description = "비구성원 또는 모임장이 아님 (NOT_GROUP_MEMBER, NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 없음 (GROUP_NOT_FOUND)"),
    )
    fun createEvent(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(hidden = true) currentUserId: Long,
        request: CreateEventRequest,
    ): ResponseEntity<ApiResponse<EventDetailResponse>>

    @Operation(summary = "행사 목록 조회", description = "모임의 미삭제 행사를 조회한다. 상태를 생략하면 전체를 반환한다.")
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "400", description = "잘못된 상태 값 또는 입력값 오류"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 없음 (GROUP_NOT_FOUND)"),
    )
    fun getEvents(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(hidden = true) currentUserId: Long,
        @Parameter(description = "행사 상태 필터. 없으면 전체", example = "ACTIVE") status: EventStatus?,
    ): ApiResponse<List<EventListResponse>>

    @Operation(summary = "행사 상세 조회", description = "행사 정보와 최초·추가·총예산, 승인 지출 및 잔여 예산을 조회한다.")
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 또는 미삭제 행사 없음 (GROUP_NOT_FOUND, EVENT_NOT_FOUND)"),
    )
    fun getEvent(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(description = "행사 ID", example = "10") eventId: Long,
        @Parameter(hidden = true) currentUserId: Long,
    ): ApiResponse<EventDetailResponse>

    @Operation(summary = "추가 예산 등록", description = "모임장이 ACTIVE 행사에 추가 예산을 등록한다.")
    @ApiResponses(
        SwaggerResponse(responseCode = "201", description = "등록 성공"),
        SwaggerResponse(responseCode = "400", description = "금액 또는 사유 입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(responseCode = "403", description = "비구성원 또는 모임장이 아님 (NOT_GROUP_MEMBER, NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 또는 미삭제 행사 없음 (GROUP_NOT_FOUND, EVENT_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "마감된 행사 (EVENT_ALREADY_CLOSED)"),
    )
    fun addBudgetAddition(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(description = "행사 ID", example = "10") eventId: Long,
        @Parameter(hidden = true) currentUserId: Long,
        request: CreateBudgetAdditionRequest,
    ): ResponseEntity<ApiResponse<Unit>>

    @Operation(
        summary = "행사 조건부 삭제",
        description = "모임장이 지출 신청 이력이 없는 ACTIVE 행사를 논리 삭제한다.",
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "삭제 성공"),
        SwaggerResponse(responseCode = "403", description = "비구성원 또는 모임장이 아님 (NOT_GROUP_MEMBER, NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 또는 미삭제 행사 없음 (GROUP_NOT_FOUND, EVENT_NOT_FOUND)"),
        SwaggerResponse(
            responseCode = "409",
            description = "마감된 행사 또는 지출 신청 이력 존재 (EVENT_ALREADY_CLOSED, EVENT_HAS_SPENDING_HISTORY)",
        ),
    )
    fun deleteEvent(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(description = "행사 ID", example = "10") eventId: Long,
        @Parameter(hidden = true) currentUserId: Long,
    ): ApiResponse<Unit>

    @Operation(
        summary = "행사 마감 미리보기",
        description = "행사 상태를 변경하지 않고 마감 조건과 현재 예산 현황을 확인한다.",
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "미리보기 조회 성공"),
        SwaggerResponse(responseCode = "400", description = "참여 인원 입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(responseCode = "403", description = "비구성원 또는 모임장이 아님 (NOT_GROUP_MEMBER, NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 또는 미삭제 행사 없음 (GROUP_NOT_FOUND, EVENT_NOT_FOUND)"),
        SwaggerResponse(
            responseCode = "409",
            description = "마감된 행사 또는 PENDING 지출 존재 (EVENT_ALREADY_CLOSED, EVENT_HAS_PENDING_SPENDING)",
        ),
    )
    fun previewClose(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(description = "행사 ID", example = "10") eventId: Long,
        @Parameter(hidden = true) currentUserId: Long,
        request: CloseEventRequest,
    ): ApiResponse<EventClosePreviewResponse>

    @Operation(
        summary = "행사 마감 확정",
        description = "마감 조건을 다시 검증하고 행사를 CLOSED 상태로 변경한 뒤 보고서 생성을 요청한다.",
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "마감 성공"),
        SwaggerResponse(responseCode = "400", description = "참여 인원 입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(responseCode = "403", description = "비구성원 또는 모임장이 아님 (NOT_GROUP_MEMBER, NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "활성 모임 또는 미삭제 행사 없음 (GROUP_NOT_FOUND, EVENT_NOT_FOUND)"),
        SwaggerResponse(
            responseCode = "409",
            description = "마감된 행사 또는 PENDING 지출 존재 (EVENT_ALREADY_CLOSED, EVENT_HAS_PENDING_SPENDING)",
        ),
    )
    fun closeEvent(
        @Parameter(description = "모임 ID", example = "1") groupId: Long,
        @Parameter(description = "행사 ID", example = "10") eventId: Long,
        @Parameter(hidden = true) currentUserId: Long,
        request: CloseEventRequest,
    ): ApiResponse<EventCloseResponse>
}
