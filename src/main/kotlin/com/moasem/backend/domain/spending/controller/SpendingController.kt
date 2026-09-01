package com.moasem.backend.domain.spending.controller

import com.moasem.backend.domain.spending.dto.CreateSpendingRequest
import com.moasem.backend.domain.spending.dto.EvidenceDownloadUrlResponse
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlResponse
import com.moasem.backend.domain.spending.dto.RejectSpendingRequest
import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.dto.SpendingListResponse
import com.moasem.backend.domain.spending.dto.UpdateSpendingRequest
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.service.SpendingApprovalService
import com.moasem.backend.domain.spending.service.SpendingService
import com.moasem.backend.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 지출 신청·증빙·승인.
 *
 * 지출은 행사에 종속되므로 항상 행사 경로 아래에서 다룬다. 경로의 행사와 지출이 실제로
 * 이어져 있는지는 서비스가 함께 확인한다. API 설명은 [SpendingControllerDocs]에 있다.
 *
 * 현재 로그인 사용자는 임시로 헤더에서 받는다. auth 도메인이 완성되면
 * @AuthenticationPrincipal 로 교체한다.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/spendings")
class SpendingController(
    private val spendingService: SpendingService,
    private val spendingApprovalService: SpendingApprovalService,
) : SpendingControllerDocs {

    @PostMapping("/evidence-upload-url")
    override fun issueEvidenceUploadUrl(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
        @Valid @RequestBody request: EvidenceUploadUrlRequest,
    ): ApiResponse<EvidenceUploadUrlResponse> =
        ApiResponse.success(spendingService.issueEvidenceUploadUrl(eventId, currentUserId, request))

    /** 리소스를 만드는 요청이라 201과 Location을 돌려준다. */
    @PostMapping
    override fun createSpending(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
        @Valid @RequestBody request: CreateSpendingRequest,
    ): ResponseEntity<ApiResponse<SpendingDetailResponse>> {
        val response = spendingService.createSpending(eventId, currentUserId, request)
        return ResponseEntity
            .created(URI.create("/api/v1/events/$eventId/spendings/${response.spendingId}"))
            .body(ApiResponse.success("지출을 신청했습니다.", response))
    }

    @PatchMapping("/{spendingId}")
    override fun updateSpending(
        @PathVariable eventId: Long,
        @PathVariable spendingId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
        @Valid @RequestBody request: UpdateSpendingRequest,
    ): ApiResponse<SpendingDetailResponse> =
        ApiResponse.success(spendingService.updateSpending(eventId, spendingId, currentUserId, request))

    /** PagedModel로 감싼다. Page 구현체를 그대로 직렬화하면 구조가 안정적이지 않다는 경고가 난다. */
    @GetMapping
    override fun getSpendings(
        @PathVariable eventId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
        @RequestParam(required = false) status: SpendingStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ApiResponse<PagedModel<SpendingListResponse>> {
        val page = spendingService.getSpendings(eventId, currentUserId, status, pageable)
        return ApiResponse.success(PagedModel(page))
    }

    @GetMapping("/{spendingId}")
    override fun getSpending(
        @PathVariable eventId: Long,
        @PathVariable spendingId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<SpendingDetailResponse> =
        ApiResponse.success(spendingService.getSpending(eventId, spendingId, currentUserId))

    @GetMapping("/{spendingId}/evidence")
    override fun issueEvidenceDownloadUrl(
        @PathVariable eventId: Long,
        @PathVariable spendingId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<EvidenceDownloadUrlResponse> =
        ApiResponse.success(spendingService.issueEvidenceDownloadUrl(eventId, spendingId, currentUserId))

    @PatchMapping("/{spendingId}/approval")
    override fun approveSpending(
        @PathVariable eventId: Long,
        @PathVariable spendingId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
    ): ApiResponse<SpendingDetailResponse> =
        ApiResponse.success("지출을 승인했습니다.", spendingApprovalService.approve(eventId, spendingId, currentUserId))

    @PatchMapping("/{spendingId}/rejection")
    override fun rejectSpending(
        @PathVariable eventId: Long,
        @PathVariable spendingId: Long,
        @RequestHeader(USER_ID_HEADER) currentUserId: Long,
        @Valid @RequestBody request: RejectSpendingRequest,
    ): ApiResponse<SpendingDetailResponse> =
        ApiResponse.success(
            "지출을 반려했습니다.",
            spendingApprovalService.reject(eventId, spendingId, currentUserId, request),
        )

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
