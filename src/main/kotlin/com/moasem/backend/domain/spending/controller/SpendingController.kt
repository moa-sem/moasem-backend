package com.moasem.backend.domain.spending.controller

import com.moasem.backend.domain.spending.dto.CreateSpendingRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlResponse
import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.service.SpendingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@Tag(name = "Spending", description = "지출 신청·증빙 API")
@RestController
@RequestMapping("/api/v1/events/{eventId}/spendings")
class SpendingController(
    private val spendingService: SpendingService,
) {

    @Operation(
        summary = "증빙 업로드 URL 발급",
        description = "증빙 이미지를 저장소에 직접 올릴 presigned URL을 발급한다. " +
            "발급받은 URL로 PUT 업로드한 뒤, 응답의 storageKey를 지출 신청 요청에 실어 보낸다.",
    )
    @PostMapping("/evidence-upload-url")
    fun issueEvidenceUploadUrl(
        @PathVariable eventId: Long,
        authentication: Authentication,
        @Valid @RequestBody request: EvidenceUploadUrlRequest,
    ): ResponseEntity<EvidenceUploadUrlResponse> =
        ResponseEntity.ok(spendingService.issueEvidenceUploadUrl(eventId, authentication.userId(), request))

    @Operation(summary = "지출 신청", description = "증빙을 첨부해 지출을 신청한다. 신청 직후 상태는 PENDING이다.")
    @PostMapping
    fun createSpending(
        @PathVariable eventId: Long,
        authentication: Authentication,
        @Valid @RequestBody request: CreateSpendingRequest,
    ): ResponseEntity<SpendingDetailResponse> {
        val response = spendingService.createSpending(eventId, authentication.userId(), request)
        return ResponseEntity
            .created(URI.create("/api/v1/events/$eventId/spendings/${response.spendingId}"))
            .body(response)
    }

    /**
     * 인증 주체에서 사용자 ID를 읽는다.
     *
     * ponytail: auth 도메인(#1)이 JWT 필터와 커스텀 principal을 붙이기 전까지의 임시 구현.
     * principal 타입이 생기면 `@AuthenticationPrincipal`로 바꾼다.
     */
    private fun Authentication.userId(): Long =
        name?.toLongOrNull() ?: throw IllegalStateException("인증 주체에서 사용자 ID를 읽을 수 없습니다.")
}
