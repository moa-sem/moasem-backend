package com.moasem.backend.domain.spending.service

import com.moasem.backend.domain.spending.dto.CreateSpendingRequest
import com.moasem.backend.domain.spending.dto.EvidenceRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlRequest
import com.moasem.backend.domain.spending.dto.UpdateSpendingRequest
import com.moasem.backend.domain.spending.entity.EvidenceType
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.entity.SpendingTag
import com.moasem.backend.domain.spending.repository.SpendingRepository
import com.moasem.backend.domain.spending.service.port.EventAccess
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider
import com.moasem.backend.global.storage.FakePrivateFileStorage
import com.moasem.backend.global.storage.FileUploadPolicy
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.error.hasErrorCode
import io.mockk.every
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpendingServiceTest {

    private val spendingRepository = mockk<SpendingRepository>()
    private val eventAccessProvider = mockk<EventAccessProvider>()
    private val groupAccessProvider = mockk<GroupAccessProvider>()
    private val fileStorage = FakePrivateFileStorage()
    private lateinit var spendingService: SpendingService

    @BeforeEach
    fun setUp() {
        spendingService = SpendingService(spendingRepository, eventAccessProvider, groupAccessProvider, fileStorage)
        every { eventAccessProvider.findAccess(EVENT_ID) } returns activeEvent()
        every { groupAccessProvider.isMember(GROUP_ID, MEMBER_ID) } returns true
    }

    @Nested
    @DisplayName("증빙 업로드 URL 발급")
    inner class IssueEvidenceUploadUrl {

        @Test
        fun `구성원에게는 행사와 사용자로 구분되는 저장 키와 함께 URL을 발급한다`() {
            val response = spendingService.issueEvidenceUploadUrl(EVENT_ID, MEMBER_ID, uploadUrlRequest())

            assertThat(response.storageKey).startsWith("spendings/$EVENT_ID/$MEMBER_ID/")
            assertThat(response.storageKey).endsWith(".jpg")
            assertThat(response.uploadUrl).contains(response.storageKey)
            assertThat(response.expiresAt).isNotNull()
        }

        @Test
        fun `발급 조건에 요청한 형식과 크기를 그대로 묶는다`() {
            spendingService.issueEvidenceUploadUrl(EVENT_ID, MEMBER_ID, uploadUrlRequest(fileSize = 2_048L))

            val issued = fileStorage.issuedUploads.single()
            assertThat(issued.contentType).isEqualTo("image/jpeg")
            assertThat(issued.contentLength).isEqualTo(2_048L)
        }

        @Test
        fun `PNG 저장 키는 png 확장자를 쓴다`() {
            val response = spendingService.issueEvidenceUploadUrl(
                EVENT_ID,
                MEMBER_ID,
                uploadUrlRequest(mimeType = "image/png"),
            )

            assertThat(response.storageKey).endsWith(".png")
        }

        @Test
        fun `허용하지 않는 형식은 URL을 발급하지 않는다`() {
            assertThatThrownBy {
                spendingService.issueEvidenceUploadUrl(EVENT_ID, MEMBER_ID, uploadUrlRequest(mimeType = "image/gif"))
            }.hasErrorCode(ErrorCode.UNSUPPORTED_FILE_TYPE)

            assertThat(fileStorage.issuedUploads).isEmpty()
        }

        @Test
        fun `용량 상한을 넘으면 URL을 발급하지 않는다`() {
            val tooLarge = FileUploadPolicy.EVIDENCE_IMAGE.maxSizeBytes + 1

            assertThatThrownBy {
                spendingService.issueEvidenceUploadUrl(EVENT_ID, MEMBER_ID, uploadUrlRequest(fileSize = tooLarge))
            }.hasErrorCode(ErrorCode.FILE_SIZE_EXCEEDED)

            assertThat(fileStorage.issuedUploads).isEmpty()
        }

        @Test
        fun `모임 구성원이 아니면 URL을 발급하지 않는다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy {
                spendingService.issueEvidenceUploadUrl(EVENT_ID, OUTSIDER_ID, uploadUrlRequest())
            }.hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            assertThat(fileStorage.issuedUploads).isEmpty()
        }
    }

    @Nested
    @DisplayName("지출 신청")
    inner class CreateSpending {

        @Test
        fun `구성원이 유효한 요청을 하면 PENDING 상태로 저장한다`() {
            val saved = captureSaved()

            val response = spendingService.createSpending(EVENT_ID, MEMBER_ID, createRequest())

            assertThat(saved.captured.status).isEqualTo(SpendingStatus.PENDING)
            assertThat(saved.captured.eventId).isEqualTo(EVENT_ID)
            assertThat(saved.captured.applicantUserId).isEqualTo(MEMBER_ID)
            assertThat(saved.captured.amount).isEqualTo(15_000L)
            assertThat(response.spendingId).isEqualTo(SPENDING_ID)
            assertThat(response.status).isEqualTo(SpendingStatus.PENDING)
        }

        @Test
        fun `상세 응답에는 증빙 저장 키를 담지 않는다`() {
            captureSaved()

            val response = spendingService.createSpending(EVENT_ID, MEMBER_ID, createRequest())

            assertThat(response.toString()).doesNotContain(evidenceKey(MEMBER_ID))
        }

        @Test
        fun `기타 태그를 고르면 상세 내용이 함께 저장된다`() {
            val saved = captureSaved()

            spendingService.createSpending(
                EVENT_ID,
                MEMBER_ID,
                createRequest(tag = SpendingTag.OTHER, otherDetail = "축하 화환"),
            )

            assertThat(saved.captured.tag).isEqualTo(SpendingTag.OTHER)
            assertThat(saved.captured.otherDetail).isEqualTo("축하 화환")
        }

        @Test
        fun `기타 태그인데 상세 내용이 없으면 저장하지 않는다`() {
            assertThatThrownBy {
                spendingService.createSpending(EVENT_ID, MEMBER_ID, createRequest(tag = SpendingTag.OTHER))
            }.isInstanceOf(IllegalArgumentException::class.java)

            verify(exactly = 0) { spendingRepository.save(any()) }
        }

        @Test
        fun `남이 발급받은 증빙 저장 키는 붙일 수 없다`() {
            assertThatThrownBy {
                spendingService.createSpending(
                    EVENT_ID,
                    MEMBER_ID,
                    createRequest(storageKey = evidenceKey(OUTSIDER_ID)),
                )
            }.hasErrorCode(ErrorCode.INVALID_EVIDENCE_KEY)

            verify(exactly = 0) { spendingRepository.save(any()) }
        }

        @Test
        fun `다른 행사에서 발급받은 증빙 저장 키는 붙일 수 없다`() {
            assertThatThrownBy {
                spendingService.createSpending(
                    EVENT_ID,
                    MEMBER_ID,
                    createRequest(storageKey = "spendings/999/$MEMBER_ID/other.jpg"),
                )
            }.hasErrorCode(ErrorCode.INVALID_EVIDENCE_KEY)
        }

        @Test
        fun `마감된 행사에는 신청할 수 없다`() {
            every { eventAccessProvider.findAccess(EVENT_ID) } returns activeEvent(isActive = false)

            assertThatThrownBy { spendingService.createSpending(EVENT_ID, MEMBER_ID, createRequest()) }
                .hasErrorCode(ErrorCode.EVENT_ALREADY_CLOSED)

            verify(exactly = 0) { spendingRepository.save(any()) }
        }

        @Test
        fun `모임 구성원이 아니면 신청할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy {
                spendingService.createSpending(EVENT_ID, OUTSIDER_ID, createRequest(storageKey = evidenceKey(OUTSIDER_ID)))
            }.hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)
        }

        @Test
        fun `구성원 여부를 마감 여부보다 먼저 검증한다`() {
            every { eventAccessProvider.findAccess(EVENT_ID) } returns activeEvent(isActive = false)
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy { spendingService.createSpending(EVENT_ID, OUTSIDER_ID, createRequest()) }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)
        }

        @Test
        fun `없는 행사에는 신청할 수 없다`() {
            every { eventAccessProvider.findAccess(EVENT_ID) } returns null

            assertThatThrownBy { spendingService.createSpending(EVENT_ID, MEMBER_ID, createRequest()) }
                .hasErrorCode(ErrorCode.EVENT_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("지출 수정")
    inner class UpdateSpending {

        @Test
        fun `본인이 신청한 PENDING 지출은 내용과 증빙을 함께 바꾼다`() {
            val spending = savedSpending()
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns spending

            val response = spendingService.updateSpending(EVENT_ID, SPENDING_ID, MEMBER_ID, updateRequest())

            assertThat(spending.amount).isEqualTo(30_000L)
            assertThat(spending.reason).isEqualTo("1일차 저녁 식사")
            assertThat(response.amount).isEqualTo(30_000L)
        }

        @Test
        fun `남이 신청한 지출은 수정할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns true
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending()

            assertThatThrownBy {
                spendingService.updateSpending(
                    EVENT_ID,
                    SPENDING_ID,
                    OUTSIDER_ID,
                    updateRequest(storageKey = evidenceKey(OUTSIDER_ID)),
                )
            }.hasErrorCode(ErrorCode.NOT_SPENDING_APPLICANT)
        }

        @Test
        fun `이미 처리된 지출은 수정할 수 없다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending(processed = true)

            assertThatThrownBy { spendingService.updateSpending(EVENT_ID, SPENDING_ID, MEMBER_ID, updateRequest()) }
                .hasErrorCode(ErrorCode.SPENDING_ALREADY_HANDLED)
        }

        @Test
        fun `다른 행사에 속한 지출은 수정할 수 없다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns null

            assertThatThrownBy { spendingService.updateSpending(EVENT_ID, SPENDING_ID, MEMBER_ID, updateRequest()) }
                .hasErrorCode(ErrorCode.SPENDING_NOT_FOUND)
        }

        @Test
        fun `수정에서도 남의 증빙 저장 키는 붙일 수 없다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending()

            assertThatThrownBy {
                spendingService.updateSpending(
                    EVENT_ID,
                    SPENDING_ID,
                    MEMBER_ID,
                    updateRequest(storageKey = evidenceKey(OUTSIDER_ID)),
                )
            }.hasErrorCode(ErrorCode.INVALID_EVIDENCE_KEY)
        }
    }

    @Nested
    @DisplayName("지출 목록 조회")
    inner class GetSpendings {

        private val pageable = PageRequest.of(0, 20)

        @Test
        fun `상태를 주지 않으면 행사의 전체 지출을 조회한다`() {
            every { spendingRepository.findAllByEventId(EVENT_ID, pageable) } returns
                PageImpl(listOf(savedSpending()), pageable, 1)

            val page = spendingService.getSpendings(EVENT_ID, MEMBER_ID, null, pageable)

            assertThat(page.content.map { it.spendingId }).containsExactly(SPENDING_ID)
            verify { spendingRepository.findAllByEventId(EVENT_ID, pageable) }
        }

        @Test
        fun `상태를 주면 해당 상태의 지출만 조회한다`() {
            every {
                spendingRepository.findAllByEventIdAndStatus(EVENT_ID, SpendingStatus.APPROVED, pageable)
            } returns PageImpl(listOf(savedSpending(processed = true)), pageable, 1)

            val page = spendingService.getSpendings(EVENT_ID, MEMBER_ID, SpendingStatus.APPROVED, pageable)

            assertThat(page.content).allSatisfy { assertThat(it.status).isEqualTo(SpendingStatus.APPROVED) }
            verify { spendingRepository.findAllByEventIdAndStatus(EVENT_ID, SpendingStatus.APPROVED, pageable) }
        }

        @Test
        fun `목록 항목에는 태그 한글 라벨이 함께 담긴다`() {
            every { spendingRepository.findAllByEventId(EVENT_ID, pageable) } returns
                PageImpl(listOf(savedSpending()), pageable, 1)

            val page = spendingService.getSpendings(EVENT_ID, MEMBER_ID, null, pageable)

            assertThat(page.content.single().tagLabel).isEqualTo("식비")
        }

        @Test
        fun `모임 구성원이 아니면 목록을 조회할 수 없다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy { spendingService.getSpendings(EVENT_ID, OUTSIDER_ID, null, pageable) }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            verify(exactly = 0) { spendingRepository.findAllByEventId(any(), any()) }
        }

        @Test
        fun `마감된 행사의 지출도 조회할 수 있다`() {
            every { eventAccessProvider.findAccess(EVENT_ID) } returns activeEvent(isActive = false)
            every { spendingRepository.findAllByEventId(EVENT_ID, pageable) } returns
                PageImpl(listOf(savedSpending()), pageable, 1)

            val page = spendingService.getSpendings(EVENT_ID, MEMBER_ID, null, pageable)

            assertThat(page.content).hasSize(1)
        }
    }

    @Nested
    @DisplayName("지출 상세 조회")
    inner class GetSpending {

        @Test
        fun `구성원은 지출 상세를 조회한다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending()

            val response = spendingService.getSpending(EVENT_ID, SPENDING_ID, MEMBER_ID)

            assertThat(response.spendingId).isEqualTo(SPENDING_ID)
            assertThat(response.eventId).isEqualTo(EVENT_ID)
        }

        @Test
        fun `다른 행사에 속한 지출은 조회할 수 없다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns null

            assertThatThrownBy { spendingService.getSpending(EVENT_ID, SPENDING_ID, MEMBER_ID) }
                .hasErrorCode(ErrorCode.SPENDING_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("증빙 이미지 조회 URL 발급")
    inner class IssueEvidenceDownloadUrl {

        @Test
        fun `구성원에게는 DB에 보관된 저장 키로 URL을 발급한다`() {
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending()

            val response = spendingService.issueEvidenceDownloadUrl(EVENT_ID, SPENDING_ID, MEMBER_ID)

            assertThat(fileStorage.issuedDownloadKeys).containsExactly(evidenceKey(MEMBER_ID))
            assertThat(response.downloadUrl).contains(evidenceKey(MEMBER_ID))
            assertThat(response.expiresAt).isNotNull()
        }

        @Test
        fun `신청자가 아니어도 같은 모임 구성원이면 발급받을 수 있다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns true
            every { spendingRepository.findByIdAndEventId(SPENDING_ID, EVENT_ID) } returns savedSpending()

            spendingService.issueEvidenceDownloadUrl(EVENT_ID, SPENDING_ID, OUTSIDER_ID)

            assertThat(fileStorage.issuedDownloadKeys).hasSize(1)
        }

        @Test
        fun `모임 구성원이 아니면 발급하지 않는다`() {
            every { groupAccessProvider.isMember(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy { spendingService.issueEvidenceDownloadUrl(EVENT_ID, SPENDING_ID, OUTSIDER_ID) }
                .hasErrorCode(ErrorCode.NOT_GROUP_MEMBER)

            assertThat(fileStorage.issuedDownloadKeys).isEmpty()
        }
    }

    private fun savedSpending(processed: Boolean = false): Spending =
        Spending.create(
            eventId = EVENT_ID,
            applicantUserId = MEMBER_ID,
            amount = 15_000L,
            spentOn = LocalDate.of(2026, 8, 20),
            reason = "1일차 점심 식사",
            tag = SpendingTag.MEAL,
            otherDetail = null,
            evidence = Spending.Evidence(EvidenceType.RECEIPT, evidenceKey(MEMBER_ID), "image/jpeg", 204_800L),
        ).also { spending ->
            val idField = Spending::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(spending, SPENDING_ID)
            if (processed) spending.approve(OWNER_ID)
        }

    private fun updateRequest(storageKey: String = evidenceKey(MEMBER_ID)) = UpdateSpendingRequest(
        amount = 30_000L,
        spentOn = LocalDate.of(2026, 8, 21),
        reason = "1일차 저녁 식사",
        tag = SpendingTag.MEAL,
        otherDetail = null,
        evidence = EvidenceRequest(
            type = EvidenceType.RECEIPT,
            storageKey = storageKey,
            mimeType = "image/jpeg",
            fileSize = 204_800L,
        ),
    )

    private fun captureSaved(): io.mockk.CapturingSlot<Spending> {
        val saved = slot<Spending>()
        every { spendingRepository.save(capture(saved)) } answers {
            val idField = Spending::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(saved.captured, SPENDING_ID)
            saved.captured
        }
        return saved
    }

    private fun activeEvent(isActive: Boolean = true) = EventAccess(EVENT_ID, GROUP_ID, isActive)

    private fun uploadUrlRequest(mimeType: String = "image/jpeg", fileSize: Long = 204_800L) =
        EvidenceUploadUrlRequest(mimeType, fileSize)

    private fun evidenceKey(userId: Long) = "spendings/$EVENT_ID/$userId/evidence.jpg"

    private fun createRequest(
        tag: SpendingTag = SpendingTag.MEAL,
        otherDetail: String? = null,
        storageKey: String = evidenceKey(MEMBER_ID),
    ) = CreateSpendingRequest(
        amount = 15_000L,
        spentOn = LocalDate.of(2026, 8, 20),
        reason = "1일차 점심 식사",
        tag = tag,
        otherDetail = otherDetail,
        evidence = EvidenceRequest(
            type = EvidenceType.RECEIPT,
            storageKey = storageKey,
            mimeType = "image/jpeg",
            fileSize = 204_800L,
        ),
    )

    companion object {
        private const val GROUP_ID = 1L
        private const val EVENT_ID = 100L
        private const val MEMBER_ID = 10L
        private const val OUTSIDER_ID = 20L
        private const val OWNER_ID = 30L
        private const val SPENDING_ID = 500L
    }
}
