package com.moasem.backend.domain.spending.controller

import com.moasem.backend.domain.spending.dto.CreateSpendingRequest
import com.moasem.backend.domain.spending.dto.EvidenceRequest
import com.moasem.backend.domain.spending.dto.RejectSpendingRequest
import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.dto.SpendingListResponse
import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.service.SpendingApprovalService
import com.moasem.backend.domain.spending.service.SpendingService
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.GlobalExceptionHandler
import com.moasem.backend.global.security.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 컨트롤러 계층 검증.
 *
 * 서비스 로직은 단위 테스트가 따로 있다. 여기서는 요청이 서비스까지 제대로 전달되는지,
 * 그리고 예외가 [GlobalExceptionHandler]를 거쳐 어떤 상태 코드로 나가는지를 본다.
 * 후자는 서비스 단위 테스트로는 절대 확인되지 않는 부분이다.
 */
@WebMvcTest(controllers = [SpendingController::class])
@Import(GlobalExceptionHandler::class)
@AutoConfigureMockMvc(addFilters = false)
class SpendingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    /** Spring 본체가 쓰는 Jackson 3 매퍼다. 컨트롤러가 역직렬화에 쓰는 것과 같은 인스턴스를 쓴다. */
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockkBean
    private lateinit var spendingService: SpendingService

    @MockkBean
    private lateinit var spendingApprovalService: SpendingApprovalService

    @Test
    @DisplayName("지출을 신청하면 201과 Location, 공통 응답을 반환한다")
    fun createSpending() {
        every { spendingService.createSpending(EVENT_ID, USER_ID, any()) } returns detailResponse()

        mockMvc.perform(
            post("/api/v1/events/$EVENT_ID/spendings")
                .header(SpendingController.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest())),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.spendingId").value(SPENDING_ID))
            .andExpect(jsonPath("$.data.tagLabel").value("식비"))
    }

    /** 증빙 저장 키는 응답에 담기지 않는다. 이미지는 조회 URL 발급 API로만 접근한다. */
    @Test
    @DisplayName("상세 응답에 증빙 저장 키가 담기지 않는다")
    fun detailHidesStorageKey() {
        every { spendingService.getSpending(EVENT_ID, SPENDING_ID, USER_ID) } returns detailResponse()

        mockMvc.perform(
            get("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID")
                .header(SpendingController.USER_ID_HEADER, USER_ID),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.evidenceStorageKey").doesNotExist())
            .andExpect(jsonPath("$.data.evidenceType").value("RECEIPT"))
    }

    @Test
    @DisplayName("목록은 페이지 정보와 함께 공통 응답으로 감싸서 반환한다")
    fun getSpendings() {
        every { spendingService.getSpendings(EVENT_ID, USER_ID, null, any()) } returns
            PageImpl(listOf(listResponse()), PageRequest.of(0, 20), 1)

        mockMvc.perform(
            get("/api/v1/events/$EVENT_ID/spendings")
                .header(SpendingController.USER_ID_HEADER, USER_ID),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].spendingId").value(SPENDING_ID))
            .andExpect(jsonPath("$.data.page.totalElements").value(1))
    }

    @Test
    @DisplayName("상태 필터가 서비스까지 전달된다")
    fun getSpendingsWithStatusFilter() {
        every { spendingService.getSpendings(EVENT_ID, USER_ID, SpendingStatus.PENDING, any()) } returns
            PageImpl(listOf(listResponse()), PageRequest.of(0, 20), 1)

        mockMvc.perform(
            get("/api/v1/events/$EVENT_ID/spendings")
                .param("status", "PENDING")
                .header(SpendingController.USER_ID_HEADER, USER_ID),
        )
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("승인 결과를 공통 응답으로 반환한다")
    fun approveSpending() {
        every { spendingApprovalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) } returns
            detailResponse(status = SpendingStatus.APPROVED, processedByUserId = OWNER_ID)

        mockMvc.perform(
            patch("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID/approval")
                .header(SpendingController.USER_ID_HEADER, OWNER_ID),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.processedByUserId").value(OWNER_ID))
    }

    @Test
    @DisplayName("모임장이 아니면 403과 NOT_GROUP_OWNER를 반환한다")
    fun approveByNonOwner() {
        every { spendingApprovalService.approve(EVENT_ID, SPENDING_ID, USER_ID) } throws
            BusinessException(ErrorCode.NOT_GROUP_OWNER)

        mockMvc.perform(
            patch("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID/approval")
                .header(SpendingController.USER_ID_HEADER, USER_ID),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"))
    }

    @Test
    @DisplayName("이미 처리된 지출이면 409와 SPENDING_ALREADY_HANDLED를 반환한다")
    fun approveAlreadyHandled() {
        every { spendingApprovalService.approve(EVENT_ID, SPENDING_ID, OWNER_ID) } throws
            BusinessException(ErrorCode.SPENDING_ALREADY_HANDLED)

        mockMvc.perform(
            patch("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID/approval")
                .header(SpendingController.USER_ID_HEADER, OWNER_ID),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SPENDING_ALREADY_HANDLED"))
    }

    @Test
    @DisplayName("반려 사유가 비어 있으면 400과 필드 오류를 반환한다")
    fun rejectWithoutReason() {
        mockMvc.perform(
            patch("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID/rejection")
                .header(SpendingController.USER_ID_HEADER, OWNER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RejectSpendingRequest(" "))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[0].field").value("reason"))
    }

    /** 기타 태그는 상세 내용이 있어야 한다. @AssertTrue가 걸러내므로 서비스까지 가지 않는다. */
    @Test
    @DisplayName("기타 태그인데 상세 내용이 없으면 400을 반환한다")
    fun createWithoutOtherDetail() {
        mockMvc.perform(
            post("/api/v1/events/$EVENT_ID/spendings")
                .header(SpendingController.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest(tag = SpendingTag.OTHER))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
    }

    /** auth 완성 전까지 사용자 ID는 헤더로 받는다. 빠지면 400이어야 한다. 500이 아니라. */
    @Test
    @DisplayName("사용자 ID 헤더가 없으면 400을 반환한다")
    fun missingUserIdHeader() {
        mockMvc.perform(get("/api/v1/events/$EVENT_ID/spendings/$SPENDING_ID"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
    }

    private fun createRequest(tag: SpendingTag = SpendingTag.MEAL) = CreateSpendingRequest(
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = tag,
        otherDetail = null,
        evidence = EvidenceRequest(
            type = EvidenceType.RECEIPT,
            storageKey = "spendings/$EVENT_ID/$USER_ID/evidence.jpg",
            mimeType = "image/jpeg",
            fileSize = 204_800L,
        ),
    )

    private fun detailResponse(
        status: SpendingStatus = SpendingStatus.PENDING,
        processedByUserId: Long? = null,
    ) = SpendingDetailResponse(
        spendingId = SPENDING_ID,
        eventId = EVENT_ID,
        applicantUserId = USER_ID,
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = SpendingTag.MEAL,
        tagLabel = SpendingTag.MEAL.label,
        otherDetail = null,
        evidenceType = EvidenceType.RECEIPT,
        status = status,
        processedByUserId = processedByUserId,
        rejectionReason = null,
        processedAt = processedByUserId?.let { LocalDateTime.of(2026, 8, 21, 10, 0) },
        createdAt = LocalDateTime.of(2026, 8, 20, 12, 0),
    )

    private fun listResponse() = SpendingListResponse(
        spendingId = SPENDING_ID,
        applicantUserId = USER_ID,
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = SpendingTag.MEAL,
        tagLabel = SpendingTag.MEAL.label,
        status = SpendingStatus.PENDING,
        createdAt = LocalDateTime.of(2026, 8, 20, 12, 0),
    )

    companion object {
        private const val EVENT_ID = 1L
        private const val SPENDING_ID = 10L
        private const val USER_ID = 42L
        private const val OWNER_ID = 7L
    }
}
