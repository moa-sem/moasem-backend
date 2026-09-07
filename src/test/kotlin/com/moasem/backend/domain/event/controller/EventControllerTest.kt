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
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.GlobalExceptionHandler
import com.moasem.backend.global.security.JwtProvider
import com.moasem.backend.global.security.SecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@WebMvcTest(controllers = [EventController::class])
@Import(GlobalExceptionHandler::class, SecurityConfig::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtProvider: JwtProvider

    @MockkBean
    private lateinit var eventService: EventService

    @MockkBean
    private lateinit var budgetAdditionService: BudgetAdditionService

    @MockkBean
    private lateinit var eventDeletionService: EventDeletionService

    @MockkBean
    private lateinit var eventClosePreviewService: EventClosePreviewService

    @MockkBean
    private lateinit var eventCloseService: EventCloseService

    @Test
    @DisplayName("행사를 생성하면 인증 사용자 ID를 전달하고 201과 Location을 반환한다")
    fun createEvent() {
        val request = createRequest()
        every { eventService.createEvent(GROUP_ID, USER_ID, request) } returns detailResponse()

        mockMvc.perform(
            post(BASE_URL)
                .with(authenticatedUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "$BASE_URL/$EVENT_ID"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.eventId").value(EVENT_ID))

        verify(exactly = 1) { eventService.createEvent(GROUP_ID, USER_ID, request) }
    }

    @Test
    @DisplayName("행사 제목이 공백이면 Controller 검증에서 400을 반환한다")
    fun createEventWithBlankTitle() {
        mockMvc.perform(
            post(BASE_URL)
                .with(authenticatedUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest(title = " "))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[0].field").value("title"))
    }

    @Test
    @DisplayName("비구성원의 행사 생성은 공통 403 응답으로 반환한다")
    fun createEventByNonMember() {
        every { eventService.createEvent(GROUP_ID, USER_ID, any()) } throws
            BusinessException(ErrorCode.NOT_GROUP_MEMBER)

        performCreate().andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"))
    }

    @Test
    @DisplayName("일반회원의 행사 생성은 공통 403 응답으로 반환한다")
    fun createEventByMember() {
        every { eventService.createEvent(GROUP_ID, USER_ID, any()) } throws
            BusinessException(ErrorCode.NOT_GROUP_OWNER)

        performCreate().andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"))
    }

    @Test
    @DisplayName("존재하지 않는 모임의 행사 생성은 공통 404 응답으로 반환한다")
    fun createEventInMissingGroup() {
        every { eventService.createEvent(GROUP_ID, USER_ID, any()) } throws
            BusinessException(ErrorCode.GROUP_NOT_FOUND)

        performCreate().andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }

    @Test
    @DisplayName("status가 없으면 전체 행사 목록을 조회한다")
    fun getAllEvents() {
        every { eventService.getEvents(GROUP_ID, USER_ID, null) } returns listOf(listResponse())

        mockMvc.perform(get(BASE_URL).with(authenticatedUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].eventId").value(EVENT_ID))

        verify(exactly = 1) { eventService.getEvents(GROUP_ID, USER_ID, null) }
    }

    @Test
    @DisplayName("ACTIVE 상태 필터를 서비스에 전달한다")
    fun getActiveEvents() {
        every { eventService.getEvents(GROUP_ID, USER_ID, EventStatus.ACTIVE) } returns listOf(listResponse())

        mockMvc.perform(get(BASE_URL).param("status", "ACTIVE").with(authenticatedUser()))
            .andExpect(status().isOk)

        verify(exactly = 1) { eventService.getEvents(GROUP_ID, USER_ID, EventStatus.ACTIVE) }
    }

    @Test
    @DisplayName("CLOSED 상태 필터를 서비스에 전달한다")
    fun getClosedEvents() {
        every { eventService.getEvents(GROUP_ID, USER_ID, EventStatus.CLOSED) } returns
            listOf(listResponse(status = EventStatus.CLOSED))

        mockMvc.perform(get(BASE_URL).param("status", "CLOSED").with(authenticatedUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
    }

    @Test
    @DisplayName("잘못된 행사 상태는 400과 INVALID_TYPE_VALUE를 반환한다")
    fun getEventsWithInvalidStatus() {
        mockMvc.perform(get(BASE_URL).param("status", "DELETED").with(authenticatedUser()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_TYPE_VALUE"))
    }

    @Test
    @DisplayName("목록에는 서비스가 반환한 미삭제 행사만 담긴다")
    fun deletedEventIsExcludedFromList() {
        every { eventService.getEvents(GROUP_ID, USER_ID, null) } returns listOf(listResponse())

        mockMvc.perform(get(BASE_URL).with(authenticatedUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].eventId").value(EVENT_ID))
    }

    @Test
    @DisplayName("행사 상세는 모든 예산 수치를 공통 응답으로 반환한다")
    fun getEvent() {
        every { eventService.getEvent(GROUP_ID, EVENT_ID, USER_ID) } returns detailResponse()

        mockMvc.perform(get("$BASE_URL/$EVENT_ID").with(authenticatedUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.initialBudget").value(500000))
            .andExpect(jsonPath("$.data.additionalBudget").value(100000))
            .andExpect(jsonPath("$.data.totalBudget").value(600000))
            .andExpect(jsonPath("$.data.approvedSpending").value(250000))
            .andExpect(jsonPath("$.data.remainingBudget").value(350000))
    }

    @Test
    @DisplayName("다른 모임의 행사는 404 EVENT_NOT_FOUND를 반환한다")
    fun getEventFromOtherGroup() {
        every { eventService.getEvent(GROUP_ID, EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.EVENT_NOT_FOUND)

        mockMvc.perform(get("$BASE_URL/$EVENT_ID").with(authenticatedUser()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
    }

    @Test
    @DisplayName("논리 삭제된 행사는 404 EVENT_NOT_FOUND를 반환한다")
    fun getDeletedEvent() {
        every { eventService.getEvent(GROUP_ID, EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.EVENT_NOT_FOUND)

        mockMvc.perform(get("$BASE_URL/$EVENT_ID").with(authenticatedUser()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
    }

    @Test
    @DisplayName("추가 예산을 등록하면 인증 사용자 ID를 전달하고 201을 반환한다")
    fun addBudgetAddition() {
        val request = budgetRequest()
        every { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, USER_ID, request) } returns Unit

        performBudgetAddition(request)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())

        verify(exactly = 1) {
            budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, USER_ID, request)
        }
    }

    @Test
    @DisplayName("추가 예산이 0원이면 400을 반환한다")
    fun addZeroBudget() {
        performBudgetAddition(budgetRequest(amount = 0L))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("amount"))
    }

    @Test
    @DisplayName("추가 예산이 음수이면 400을 반환한다")
    fun addNegativeBudget() {
        performBudgetAddition(budgetRequest(amount = -1L))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
    }

    @Test
    @DisplayName("추가 예산 사유가 공백이면 400을 반환한다")
    fun addBudgetWithBlankReason() {
        performBudgetAddition(budgetRequest(reason = " "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("reason"))
    }

    @Test
    @DisplayName("일반회원의 추가 예산 등록은 403을 반환한다")
    fun addBudgetByMember() {
        every { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, USER_ID, any()) } throws
            BusinessException(ErrorCode.NOT_GROUP_OWNER)

        performBudgetAddition().andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"))
    }

    @Test
    @DisplayName("CLOSED 행사에는 추가 예산을 등록할 수 없다")
    fun addBudgetToClosedEvent() {
        every { budgetAdditionService.addBudgetAddition(GROUP_ID, EVENT_ID, USER_ID, any()) } throws
            BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)

        performBudgetAddition().andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_CLOSED"))
    }

    @Test
    @DisplayName("행사 삭제는 인증 사용자 ID를 전달하고 공통 성공 응답을 반환한다")
    fun deleteEvent() {
        every { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, USER_ID) } returns Unit

        performDelete()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())

        verify(exactly = 1) { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, USER_ID) }
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 행사 삭제는 404를 반환한다")
    fun deleteMissingEvent() {
        every { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.EVENT_NOT_FOUND)

        performDelete()
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
    }

    @Test
    @DisplayName("일반회원의 행사 삭제는 403을 반환한다")
    fun deleteEventByMember() {
        every { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.NOT_GROUP_OWNER)

        performDelete()
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"))
    }

    @Test
    @DisplayName("지출 신청 이력이 있는 행사 삭제는 409를 반환한다")
    fun deleteEventWithSpendingHistory() {
        every { eventDeletionService.deleteEvent(GROUP_ID, EVENT_ID, USER_ID) } throws
            BusinessException(ErrorCode.EVENT_HAS_SPENDING_HISTORY)

        performDelete()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_HAS_SPENDING_HISTORY"))
    }

    @Test
    @DisplayName("마감 미리보기는 참여 인원과 인증 사용자 ID를 전달하고 예산 현황을 반환한다")
    fun previewClose() {
        every {
            eventClosePreviewService.previewClose(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } returns closePreviewResponse()

        performClosePreview()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participantCount").value(PARTICIPANT_COUNT))
            .andExpect(jsonPath("$.data.initialBudget").value(500000))
            .andExpect(jsonPath("$.data.additionalBudget").value(100000))
            .andExpect(jsonPath("$.data.totalBudget").value(600000))
            .andExpect(jsonPath("$.data.approvedSpending").value(250000))
            .andExpect(jsonPath("$.data.remainingBudget").value(350000))

        verify(exactly = 1) {
            eventClosePreviewService.previewClose(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        }
        verify(exactly = 0) { eventCloseService.closeEvent(any(), any(), any(), any()) }
        verify(exactly = 0) { eventDeletionService.deleteEvent(any(), any(), any()) }
    }

    @Test
    @DisplayName("마감 미리보기 참여 인원이 0명이면 400이고 서비스를 호출하지 않는다")
    fun previewCloseWithZeroParticipants() {
        performClosePreview(participantCount = 0)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[0].field").value("participantCount"))

        verify(exactly = 0) { eventClosePreviewService.previewClose(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("마감 미리보기 참여 인원이 음수이면 400이다")
    fun previewCloseWithNegativeParticipants() {
        performClosePreview(participantCount = -1)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
    }

    @Test
    @DisplayName("PENDING 지출이 있으면 마감 미리보기는 409를 반환한다")
    fun previewCloseWithPendingSpending() {
        every {
            eventClosePreviewService.previewClose(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } throws BusinessException(ErrorCode.EVENT_HAS_PENDING_SPENDING)

        performClosePreview()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_HAS_PENDING_SPENDING"))
    }

    @Test
    @DisplayName("이미 마감된 행사의 마감 미리보기는 409를 반환한다")
    fun previewAlreadyClosedEvent() {
        every {
            eventClosePreviewService.previewClose(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } throws BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)

        performClosePreview()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_CLOSED"))
    }

    @Test
    @DisplayName("행사 마감은 참여 인원과 인증 사용자 ID를 전달하고 확정 결과를 반환한다")
    fun closeEvent() {
        every {
            eventCloseService.closeEvent(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } returns closeResponse()

        performClose()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CLOSED"))
            .andExpect(jsonPath("$.data.participantCount").value(PARTICIPANT_COUNT))
            .andExpect(jsonPath("$.data.closedAt").value("2026-09-12T13:00:00"))

        verify(exactly = 1) {
            eventCloseService.closeEvent(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        }
    }

    @Test
    @DisplayName("행사 마감 참여 인원이 0명이면 400이고 서비스를 호출하지 않는다")
    fun closeEventWithZeroParticipants() {
        performClose(participantCount = 0)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))

        verify(exactly = 0) { eventCloseService.closeEvent(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("행사 마감 참여 인원이 음수이면 400이다")
    fun closeEventWithNegativeParticipants() {
        performClose(participantCount = -1)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("participantCount"))
    }

    @Test
    @DisplayName("PENDING 지출이 있으면 행사 마감은 409를 반환한다")
    fun closeEventWithPendingSpending() {
        every {
            eventCloseService.closeEvent(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } throws BusinessException(ErrorCode.EVENT_HAS_PENDING_SPENDING)

        performClose()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_HAS_PENDING_SPENDING"))
    }

    @Test
    @DisplayName("이미 마감된 행사의 마감 확정은 409를 반환한다")
    fun closeAlreadyClosedEvent() {
        every {
            eventCloseService.closeEvent(GROUP_ID, EVENT_ID, USER_ID, PARTICIPANT_COUNT)
        } throws BusinessException(ErrorCode.EVENT_ALREADY_CLOSED)

        performClose()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_CLOSED"))
    }

    private fun performCreate() = mockMvc.perform(
        post(BASE_URL)
            .with(authenticatedUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest())),
    )

    private fun performBudgetAddition(request: CreateBudgetAdditionRequest = budgetRequest()) = mockMvc.perform(
        post("$BASE_URL/$EVENT_ID/budget-additions")
            .with(authenticatedUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)),
    )

    private fun performDelete() = mockMvc.perform(
        delete("$BASE_URL/$EVENT_ID").with(authenticatedUser()),
    )

    private fun performClosePreview(participantCount: Int = PARTICIPANT_COUNT) = mockMvc.perform(
        post("$BASE_URL/$EVENT_ID/close-preview")
            .with(authenticatedUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(CloseEventRequest(participantCount))),
    )

    private fun performClose(participantCount: Int = PARTICIPANT_COUNT) = mockMvc.perform(
        post("$BASE_URL/$EVENT_ID/close")
            .with(authenticatedUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(CloseEventRequest(participantCount))),
    )

    private fun createRequest(title: String = "여름 MT") = CreateEventRequest(
        title = title,
        description = "2박 3일 행사",
        startAt = START_AT,
        endAt = END_AT,
        initialBudget = 500_000L,
    )

    private fun budgetRequest(amount: Long = 100_000L, reason: String = "참가 인원 증가") =
        CreateBudgetAdditionRequest(amount, reason)

    private fun authenticatedUser(): RequestPostProcessor = authentication(
        UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()),
    )

    private fun listResponse(status: EventStatus = EventStatus.ACTIVE) = EventListResponse(
        eventId = EVENT_ID,
        title = "여름 MT",
        startAt = START_AT,
        endAt = END_AT,
        status = status,
        initialBudget = 500_000L,
    )

    private fun detailResponse() = EventDetailResponse(
        eventId = EVENT_ID,
        groupId = GROUP_ID,
        title = "여름 MT",
        description = "2박 3일 행사",
        startAt = START_AT,
        endAt = END_AT,
        status = EventStatus.ACTIVE,
        initialBudget = 500_000L,
        additionalBudget = 100_000L,
        totalBudget = 600_000L,
        approvedSpending = 250_000L,
        remainingBudget = 350_000L,
    )

    private fun closePreviewResponse() = EventClosePreviewResponse(
        eventId = EVENT_ID,
        title = "여름 MT",
        status = EventStatus.ACTIVE,
        participantCount = PARTICIPANT_COUNT,
        pendingSpendingCount = 0L,
        initialBudget = 500_000L,
        additionalBudget = 100_000L,
        totalBudget = 600_000L,
        approvedSpending = 250_000L,
        remainingBudget = 350_000L,
    )

    private fun closeResponse() = EventCloseResponse(
        eventId = EVENT_ID,
        status = EventStatus.CLOSED,
        participantCount = PARTICIPANT_COUNT,
        closedAt = LocalDateTime.of(2026, 9, 12, 13, 0),
    )

    companion object {
        private const val GROUP_ID = 1L
        private const val EVENT_ID = 10L
        private const val USER_ID = 42L
        private const val PARTICIPANT_COUNT = 12
        private const val BASE_URL = "/api/v1/groups/$GROUP_ID/events"
        private val START_AT = LocalDateTime.of(2026, 9, 10, 10, 0)
        private val END_AT = LocalDateTime.of(2026, 9, 12, 12, 0)
    }
}
