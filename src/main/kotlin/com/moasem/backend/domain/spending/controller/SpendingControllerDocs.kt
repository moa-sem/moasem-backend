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
import com.moasem.backend.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse

/**
 * 지출 신청·증빙·승인 API 문서.
 *
 * Swagger 어노테이션을 여기에 모아 컨트롤러에는 매핑과 위임만 남긴다.
 * springdoc은 구현 클래스뿐 아니라 인터페이스에 선언된 어노테이션도 읽는다.
 *
 * 공통 응답 래퍼와 이름이 겹쳐 Swagger 쪽 응답 어노테이션은 SwaggerResponse로 별칭을 준다.
 */
@Tag(name = "Spending", description = "지출 신청·증빙·승인")
interface SpendingControllerDocs {

    @Operation(
        summary = "증빙 업로드 URL 발급",
        description = """
            증빙 이미지를 저장소에 직접 올릴 presigned URL을 발급한다.

            파일은 서버를 거치지 않는다. 발급받은 `uploadUrl`로 **PUT** 업로드한 뒤,
            응답의 `storageKey`를 지출 신청 요청에 그대로 실어 보낸다.

            형식과 크기는 발급된 URL에 묶인다. 여기서 신고한 값과 다르게 올리면 저장소가 거부하므로,
            실제 파일의 값을 정확히 보내야 한다. JPEG·PNG만, 10MB 이하만 허용한다.

            URL은 5분 뒤 만료된다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "발급 성공"),
        SwaggerResponse(
            responseCode = "400",
            description = "허용하지 않는 형식 (UNSUPPORTED_FILE_TYPE) 또는 용량 초과 (FILE_SIZE_EXCEEDED)",
        ),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "행사 없음 (EVENT_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "마감된 행사 (EVENT_ALREADY_CLOSED)"),
    )
    fun issueEvidenceUploadUrl(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID. auth 완성 전까지 임시로 헤더로 받는다", example = "42")
        currentUserId: Long,
        request: EvidenceUploadUrlRequest,
    ): ApiResponse<EvidenceUploadUrlResponse>

    @Operation(
        summary = "지출 신청",
        description = """
            증빙을 첨부해 지출을 신청한다. 신청 직후 상태는 항상 PENDING이며,
            모임장이 승인하기 전까지 예산에 반영되지 않는다.

            `evidence.storageKey`는 증빙 업로드 URL 발급 응답에서 받은 값이어야 한다.
            본인이 이 행사에서 발급받은 키가 아니면 거부한다.

            태그가 `OTHER`면 `otherDetail`이 필수다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "201", description = "신청 성공. Location 헤더에 상세 조회 경로가 담긴다"),
        SwaggerResponse(responseCode = "400", description = "입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(
            responseCode = "403",
            description = "모임 구성원이 아님 (NOT_GROUP_MEMBER) 또는 본인이 발급받은 증빙 키가 아님 (INVALID_EVIDENCE_KEY)",
        ),
        SwaggerResponse(responseCode = "404", description = "행사 없음 (EVENT_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "마감된 행사 (EVENT_ALREADY_CLOSED)"),
    )
    fun createSpending(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID", example = "42") currentUserId: Long,
        request: CreateSpendingRequest,
    ): ResponseEntity<ApiResponse<SpendingDetailResponse>>

    @Operation(
        summary = "지출 수정",
        description = """
            본인이 신청한 PENDING 상태의 지출을 수정한다.

            증빙까지 통째로 교체하므로 신청 내용 **전체**를 보낸다. 바꾸지 않을 값도 그대로 실어야 한다.
            증빙을 바꾸지 않더라도 기존 `storageKey`를 다시 보내면 된다.

            이미 승인·반려된 건은 수정할 수 없다. 처리된 건을 고치면 승인 근거가 사라지기 때문이다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "수정 성공"),
        SwaggerResponse(responseCode = "400", description = "입력값 오류 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(
            responseCode = "403",
            description = "본인 신청이 아님 (NOT_SPENDING_APPLICANT), 구성원 아님 (NOT_GROUP_MEMBER), 증빙 키 불일치 (INVALID_EVIDENCE_KEY)",
        ),
        SwaggerResponse(responseCode = "404", description = "행사 또는 지출 없음 (EVENT_NOT_FOUND, SPENDING_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "이미 처리된 지출 (SPENDING_ALREADY_HANDLED)"),
    )
    fun updateSpending(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "지출 ID", example = "10") spendingId: Long,
        @Parameter(description = "현재 로그인 사용자 ID", example = "42") currentUserId: Long,
        request: UpdateSpendingRequest,
    ): ApiResponse<SpendingDetailResponse>

    @Operation(
        summary = "지출 목록 조회",
        description = """
            행사의 지출을 조회한다. `status`를 주면 해당 상태만 걸러낸다.

            기본 정렬은 신청 시각 내림차순, 기본 크기는 20이다.
            모임 구성원이면 남이 신청한 건도 볼 수 있다. 예산을 함께 쓰는 사이라 서로의 지출이 보여야 한다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "행사 없음 (EVENT_NOT_FOUND)"),
    )
    fun getSpendings(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "현재 로그인 사용자 ID", example = "42") currentUserId: Long,
        @Parameter(description = "상태 필터. 없으면 전체") status: SpendingStatus?,
        pageable: Pageable,
    ): ApiResponse<PagedModel<SpendingListResponse>>

    @Operation(
        summary = "지출 상세 조회",
        description = """
            지출 한 건의 상세를 조회한다.

            증빙 이미지의 저장 키는 응답에 담기지 않는다. 이미지는 증빙 조회 URL 발급 API로만 접근한다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "조회 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "행사 또는 지출 없음 (EVENT_NOT_FOUND, SPENDING_NOT_FOUND)"),
    )
    fun getSpending(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "지출 ID", example = "10") spendingId: Long,
        @Parameter(description = "현재 로그인 사용자 ID", example = "42") currentUserId: Long,
    ): ApiResponse<SpendingDetailResponse>

    @Operation(
        summary = "증빙 이미지 조회 URL 발급",
        description = """
            증빙 이미지를 볼 수 있는 임시 URL을 발급한다. 5분 뒤 만료된다.

            발급된 URL은 그 자체가 통행증이라, 유효 기간 동안은 링크를 아는 누구나 열어볼 수 있다.
            그래서 모임 구성원 여부를 확인한 뒤에만 발급한다.

            저장 키는 요청에서 받지 않고 서버가 DB에 보관한 값을 쓴다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "발급 성공"),
        SwaggerResponse(responseCode = "403", description = "모임 구성원이 아님 (NOT_GROUP_MEMBER)"),
        SwaggerResponse(responseCode = "404", description = "행사 또는 지출 없음 (EVENT_NOT_FOUND, SPENDING_NOT_FOUND)"),
    )
    fun issueEvidenceDownloadUrl(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "지출 ID", example = "10") spendingId: Long,
        @Parameter(description = "현재 로그인 사용자 ID", example = "42") currentUserId: Long,
    ): ApiResponse<EvidenceDownloadUrlResponse>

    @Operation(
        summary = "지출 승인",
        description = """
            모임장이 PENDING 지출을 승인한다.

            승인 후 잔여 예산이 음수가 되어도 승인 자체는 허용한다. 초과 지출은 결산에서 드러낼 값이지
            승인을 거부할 조건이 아니다.

            같은 건에 승인·반려가 동시에 들어와도 한 번만 처리된다. 뒤에 도착한 요청은 409를 받는다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "승인 성공"),
        SwaggerResponse(responseCode = "403", description = "모임장이 아님 (NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "행사 또는 지출 없음 (EVENT_NOT_FOUND, SPENDING_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "이미 처리된 지출 (SPENDING_ALREADY_HANDLED)"),
    )
    fun approveSpending(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "지출 ID", example = "10") spendingId: Long,
        @Parameter(description = "현재 로그인 사용자 ID(모임장)", example = "7") currentUserId: Long,
    ): ApiResponse<SpendingDetailResponse>

    @Operation(
        summary = "지출 반려",
        description = """
            모임장이 PENDING 지출을 반려한다. 반려 사유는 필수다.

            신청자가 무엇을 고쳐야 하는지 알 수 있어야 하고, 반려 기록은 결산에도 남는다.
            반려된 건은 다시 승인할 수 없다.
        """,
    )
    @ApiResponses(
        SwaggerResponse(responseCode = "200", description = "반려 성공"),
        SwaggerResponse(responseCode = "400", description = "반려 사유 누락 (INVALID_INPUT_VALUE)"),
        SwaggerResponse(responseCode = "403", description = "모임장이 아님 (NOT_GROUP_OWNER)"),
        SwaggerResponse(responseCode = "404", description = "행사 또는 지출 없음 (EVENT_NOT_FOUND, SPENDING_NOT_FOUND)"),
        SwaggerResponse(responseCode = "409", description = "이미 처리된 지출 (SPENDING_ALREADY_HANDLED)"),
    )
    fun rejectSpending(
        @Parameter(description = "행사 ID", example = "1") eventId: Long,
        @Parameter(description = "지출 ID", example = "10") spendingId: Long,
        @Parameter(description = "현재 로그인 사용자 ID(모임장)", example = "7") currentUserId: Long,
        request: RejectSpendingRequest,
    ): ApiResponse<SpendingDetailResponse>
}
