package com.moasem.backend.domain.spending.service

import com.moasem.backend.domain.spending.dto.CreateSpendingRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlRequest
import com.moasem.backend.domain.spending.dto.EvidenceUploadUrlResponse
import com.moasem.backend.domain.spending.dto.EvidenceDownloadUrlResponse
import com.moasem.backend.domain.spending.dto.ReceiptOcrRequest
import com.moasem.backend.domain.spending.dto.ReceiptOcrResponse
import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.dto.SpendingListResponse
import com.moasem.backend.domain.spending.dto.UpdateSpendingRequest
import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import com.moasem.backend.domain.spending.repository.SpendingRepository
import com.moasem.backend.domain.spending.service.port.EventAccess
import com.moasem.backend.domain.spending.service.port.EventAccessProvider
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider
import com.moasem.backend.domain.spending.service.port.ReceiptOcrClient
import com.moasem.backend.domain.spending.service.port.ReceiptOcrResult
import com.moasem.backend.domain.spending.service.port.UserNameProvider
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import com.moasem.backend.global.storage.FileUploadPolicy
import com.moasem.backend.global.storage.PrivateFileStorage
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class SpendingService(
    private val spendingRepository: SpendingRepository,
    private val eventAccessProvider: EventAccessProvider,
    private val groupAccessProvider: GroupAccessProvider,
    private val privateFileStorage: PrivateFileStorage,
    private val userNameProvider: UserNameProvider,
    private val receiptOcrClient: ReceiptOcrClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 증빙 이미지를 올릴 presigned URL을 발급한다.
     *
     * 파일은 서버를 거치지 않으므로 형식·용량 검증은 여기서 끝내고 발급 조건에 묶는다.
     * 저장 키에 행사와 사용자를 넣어, 남이 올린 증빙의 키를 자기 신청에 붙일 수 없게 한다.
     */
    @Transactional(readOnly = true)
    fun issueEvidenceUploadUrl(
        eventId: Long,
        currentUserId: Long,
        request: EvidenceUploadUrlRequest,
    ): EvidenceUploadUrlResponse {
        validateApplicable(eventId, currentUserId)
        FileUploadPolicy.EVIDENCE_IMAGE.validate(request.mimeType, request.fileSize)

        val storageKey = buildEvidenceKey(eventId, currentUserId, request.mimeType)
        val uploadUrl = privateFileStorage.issueUploadUrl(
            key = storageKey,
            contentType = request.mimeType,
            contentLength = request.fileSize,
            expiry = UPLOAD_URL_EXPIRY,
        )

        return EvidenceUploadUrlResponse(
            uploadUrl = uploadUrl,
            storageKey = storageKey,
            expiresAt = LocalDateTime.now().plus(UPLOAD_URL_EXPIRY),
        )
    }

    /**
     * 업로드된 영수증을 읽어 신청 폼에 채울 값을 돌려준다. 결과는 저장하지 않는다.
     *
     * 인식 실패는 오류로 내보내지 않고 빈 결과를 준다. 입력을 돕는 기능일 뿐이라
     * 사용자는 직접 채우면 된다. 업로드가 덜 끝나 파일이 없는 경우만 오류로 알린다.
     *
     * 트랜잭션을 걸지 않는다. 외부 API를 기다리는 동안 DB 커넥션을 붙잡지 않기 위해서다.
     */
    fun recognizeReceipt(
        eventId: Long,
        currentUserId: Long,
        request: ReceiptOcrRequest,
    ): ReceiptOcrResponse {
        validateApplicable(eventId, currentUserId)
        validateEvidenceKeyOwnership(request.storageKey, eventId, currentUserId)

        val image = privateFileStorage.read(request.storageKey)
            ?: throw BusinessException(ErrorCode.EVIDENCE_FILE_NOT_FOUND)

        val result = try {
            receiptOcrClient.recognize(image, mimeTypeOf(request.storageKey))
        } catch (e: Exception) {
            log.warn("영수증 인식 실패. eventId={} key={}", eventId, request.storageKey, e)
            ReceiptOcrResult.EMPTY
        }
        return ReceiptOcrResponse.from(result)
    }

    /** 지출을 신청한다. 항상 PENDING으로 시작하며 모임장이 처리하기 전까지 예산에 반영되지 않는다. */
    @Transactional
    fun createSpending(
        eventId: Long,
        currentUserId: Long,
        request: CreateSpendingRequest,
    ): SpendingDetailResponse {
        validateApplicable(eventId, currentUserId)

        val evidence = requireNotNull(request.evidence) { "증빙 정보는 필수입니다." }
        validateEvidenceKeyOwnership(evidence.storageKey, eventId, currentUserId)

        val spending = Spending.create(
            eventId = eventId,
            applicantUserId = currentUserId,
            amount = request.amount,
            spentOn = requireNotNull(request.spentOn) { "지출일은 필수입니다." },
            reason = request.reason,
            tag = requireNotNull(request.tag) { "지출 태그는 필수입니다." },
            otherDetail = request.otherDetail,
            evidence = Spending.Evidence(
                type = requireNotNull(evidence.type) { "증빙 종류는 필수입니다." },
                storageKey = evidence.storageKey,
                mimeType = evidence.mimeType,
                fileSize = evidence.fileSize,
            ),
        )

        return SpendingDetailResponse.from(spendingRepository.save(spending))
    }

    /**
     * 신청 내용을 수정한다.
     *
     * 본인 확인만 여기서 하고, PENDING 여부는 엔티티가 막는다. 처리된 건을 고치면
     * 승인 근거가 사라지므로 그 규칙은 승인·반려와 한곳에서 강제되어야 한다.
     */
    @Transactional
    fun updateSpending(
        eventId: Long,
        spendingId: Long,
        currentUserId: Long,
        request: UpdateSpendingRequest,
    ): SpendingDetailResponse {
        findAccessibleEvent(eventId, currentUserId)

        val spending = findSpendingOf(eventId, spendingId)
        if (!spending.isApplicant(currentUserId)) throw BusinessException(ErrorCode.NOT_SPENDING_APPLICANT)

        val evidence = requireNotNull(request.evidence) { "증빙 정보는 필수입니다." }
        validateEvidenceKeyOwnership(evidence.storageKey, eventId, currentUserId)

        spending.update(
            amount = request.amount,
            spentOn = requireNotNull(request.spentOn) { "지출일은 필수입니다." },
            reason = request.reason,
            tag = requireNotNull(request.tag) { "지출 태그는 필수입니다." },
            otherDetail = request.otherDetail,
            evidence = Spending.Evidence(
                type = requireNotNull(evidence.type) { "증빙 종류는 필수입니다." },
                storageKey = evidence.storageKey,
                mimeType = evidence.mimeType,
                fileSize = evidence.fileSize,
            ),
        )

        return SpendingDetailResponse.from(spending)
    }

    /** 행사의 지출 목록을 조회한다. [status]가 없으면 상태를 가리지 않는다. */
    @Transactional(readOnly = true)
    fun getSpendings(
        eventId: Long,
        currentUserId: Long,
        status: SpendingStatus?,
        pageable: Pageable,
    ): Page<SpendingListResponse> {
        findAccessibleEvent(eventId, currentUserId)

        val spendings = if (status == null) {
            spendingRepository.findAllByEventId(eventId, pageable)
        } else {
            spendingRepository.findAllByEventIdAndStatus(eventId, status, pageable)
        }

        // 한 페이지에 필요한 이름을 한 번에 모아 온다. 건별로 물어보면 지출 수만큼 조회가 나간다.
        val names = userNameProvider.findNames(spendings.content.map { it.applicantUserId })
        return spendings.map { SpendingListResponse.from(it, names[it.applicantUserId]) }
    }

    @Transactional(readOnly = true)
    fun getSpending(eventId: Long, spendingId: Long, currentUserId: Long): SpendingDetailResponse {
        findAccessibleEvent(eventId, currentUserId)
        return SpendingDetailResponse.from(findSpendingOf(eventId, spendingId))
    }

    /**
     * 증빙 이미지를 볼 수 있는 URL을 발급한다.
     *
     * 발급된 URL은 그 자체가 통행증이므로 구성원 검증을 먼저 끝낸다. 저장 키는 요청에서 받지 않고
     * DB에 보관된 값만 쓴다.
     */
    @Transactional(readOnly = true)
    fun issueEvidenceDownloadUrl(
        eventId: Long,
        spendingId: Long,
        currentUserId: Long,
    ): EvidenceDownloadUrlResponse {
        findAccessibleEvent(eventId, currentUserId)

        val spending = findSpendingOf(eventId, spendingId)
        val downloadUrl = privateFileStorage.issueDownloadUrl(spending.evidenceStorageKey, DOWNLOAD_URL_EXPIRY)

        return EvidenceDownloadUrlResponse(
            downloadUrl = downloadUrl,
            expiresAt = LocalDateTime.now().plus(DOWNLOAD_URL_EXPIRY),
        )
    }

    /**
     * 지출을 올릴 수 있는 상태인지 확인한다.
     *
     * 구성원 검증을 마감 검증보다 먼저 한다. 남의 모임 행사가 마감됐는지 여부까지
     * 알려줄 이유가 없기 때문이다.
     */
    private fun validateApplicable(eventId: Long, userId: Long) {
        val access = findAccessibleEvent(eventId, userId)
        if (!access.isActive) {
            throw BusinessException(ErrorCode.EVENT_ALREADY_CLOSED, "마감된 행사에는 지출을 신청할 수 없습니다.")
        }
    }

    /** 요청자가 들여다봐도 되는 행사인지 확인하고 행사 정보를 돌려준다. */
    private fun findAccessibleEvent(eventId: Long, userId: Long): EventAccess {
        require(eventId > 0) { "행사 ID는 양수여야 합니다." }
        require(userId > 0) { "사용자 ID는 양수여야 합니다." }

        val access = eventAccessProvider.findAccess(eventId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)
        if (!groupAccessProvider.isMember(access.groupId, userId)) {
            throw BusinessException(ErrorCode.NOT_GROUP_MEMBER)
        }
        return access
    }

    /** 경로의 행사에 실제로 속한 지출인지까지 확인해서 가져온다. */
    private fun findSpendingOf(eventId: Long, spendingId: Long): Spending =
        spendingRepository.findByIdAndEventId(spendingId, eventId)
            ?: throw BusinessException(ErrorCode.SPENDING_NOT_FOUND)

    /**
     * 신청에 실린 저장 키가 본인이 이 행사에서 발급받은 것인지 확인한다.
     *
     * 저장 키는 클라이언트가 보내는 값이므로 그대로 믿으면 남의 증빙을 자기 신청에 붙일 수 있다.
     * 접두사만 보면 `../`로 다른 사람의 경로를 가리킬 수 있어 상위 경로 표기도 막는다.
     */
    private fun validateEvidenceKeyOwnership(storageKey: String, eventId: Long, userId: Long) {
        if (!storageKey.startsWith(evidenceKeyPrefix(eventId, userId)) || ".." in storageKey) {
            throw BusinessException(
                ErrorCode.INVALID_EVIDENCE_KEY,
                "본인이 이 행사에서 발급받은 증빙 저장 키만 사용할 수 있습니다.",
            )
        }
    }

    private fun buildEvidenceKey(eventId: Long, userId: Long, mimeType: String): String =
        "${evidenceKeyPrefix(eventId, userId)}${UUID.randomUUID()}.${extensionOf(mimeType)}"

    private fun evidenceKeyPrefix(eventId: Long, userId: Long): String = "spendings/$eventId/$userId/"

    /** 저장 키는 항상 [buildEvidenceKey]가 만든 jpg·png다. */
    private fun mimeTypeOf(storageKey: String): String =
        if (storageKey.endsWith(".png")) "image/png" else "image/jpeg"

    private fun extensionOf(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        else -> throw IllegalArgumentException("허용하지 않는 파일 형식입니다. mimeType=$mimeType")
    }

    companion object {
        /** 발급된 URL은 그 자체가 통행증이므로 짧게 유지한다. */
        private val UPLOAD_URL_EXPIRY: Duration = Duration.ofMinutes(5)
        private val DOWNLOAD_URL_EXPIRY: Duration = Duration.ofMinutes(5)
    }
}
