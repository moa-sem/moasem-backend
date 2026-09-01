package com.moasem.backend.domain.spending.entity

import com.moasem.backend.global.storage.FileUploadPolicy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 행사 참가자가 올린 지출 신청 한 건.
 *
 * 신청 시점에는 항상 [SpendingStatus.PENDING]으로 시작하며, 모임장이 승인·반려하기 전까지만
 * 신청자가 수정할 수 있다. 승인·반려는 한 번만 가능하고 되돌릴 수 없다.
 *
 * 증빙 이미지는 비공개 저장소에 두고 여기에는 접근 키([evidenceStorageKey])만 보관한다.
 */
@Entity
@Table(name = "spendings")
class Spending protected constructor(
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: Long,
    @Column(name = "applicant_user_id", nullable = false, updatable = false)
    val applicantUserId: Long,
    amount: Long,
    spentOn: LocalDate,
    reason: String,
    tag: SpendingTag,
    otherDetail: String?,
    evidence: Evidence,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    @Column(name = "amount", nullable = false)
    var amount: Long = amount
        protected set

    @Column(name = "spent_on", nullable = false)
    var spentOn: LocalDate = spentOn
        protected set

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    var reason: String = reason
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 20)
    var tag: SpendingTag = tag
        protected set

    /** [SpendingTag.OTHER]일 때만 채워지는 상세 설명. 다른 태그로 바뀌면 비운다. */
    @Column(name = "other_detail", columnDefinition = "text")
    var otherDetail: String? = otherDetail
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    var evidenceType: EvidenceType = evidence.type
        protected set

    @Column(name = "evidence_storage_key", nullable = false, columnDefinition = "text")
    var evidenceStorageKey: String = evidence.storageKey
        protected set

    @Column(name = "evidence_mime_type", nullable = false, length = 50)
    var evidenceMimeType: String = evidence.mimeType
        protected set

    @Column(name = "evidence_file_size")
    var evidenceFileSize: Long? = evidence.fileSize
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SpendingStatus = SpendingStatus.PENDING
        protected set

    @Column(name = "processed_by_user_id")
    var processedByUserId: Long? = null
        protected set

    @Column(name = "rejection_reason", columnDefinition = "text")
    var rejectionReason: String? = null
        protected set

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null

    val isPending: Boolean
        get() = status == SpendingStatus.PENDING

    fun isApplicant(userId: Long): Boolean = applicantUserId == userId

    /**
     * 신청 내용을 수정한다. 증빙까지 통째로 갈아끼운다.
     *
     * 이미 처리된 건을 고치면 승인 근거가 사라지므로 PENDING일 때만 허용한다.
     */
    fun update(
        amount: Long,
        spentOn: LocalDate,
        reason: String,
        tag: SpendingTag,
        otherDetail: String?,
        evidence: Evidence,
    ) {
        checkPending("수정")
        validate(amount, reason, tag, otherDetail)

        this.amount = amount
        this.spentOn = spentOn
        this.reason = reason.trim()
        this.tag = tag
        this.otherDetail = normalizeOtherDetail(tag, otherDetail)
        this.evidenceType = evidence.type
        this.evidenceStorageKey = evidence.storageKey
        this.evidenceMimeType = evidence.mimeType
        this.evidenceFileSize = evidence.fileSize
    }

    /**
     * 지출을 승인한다.
     *
     * 승인 후 잔여 예산이 음수가 되어도 승인 자체는 막지 않는다(기획안 8.2). 예산 초과는
     * 결산에서 드러내는 값이지 승인을 거부할 조건이 아니다.
     */
    fun approve(processorUserId: Long) {
        checkPending("승인")
        require(processorUserId > 0) { "처리자 ID는 양수여야 합니다." }

        status = SpendingStatus.APPROVED
        processedByUserId = processorUserId
        processedAt = LocalDateTime.now()
    }

    /** 지출을 반려한다. 신청자가 무엇을 고쳐야 하는지 알 수 있도록 사유가 반드시 있어야 한다. */
    fun reject(processorUserId: Long, rejectionReason: String) {
        checkPending("반려")
        require(processorUserId > 0) { "처리자 ID는 양수여야 합니다." }
        require(rejectionReason.isNotBlank()) { "반려 사유는 비어 있을 수 없습니다." }

        status = SpendingStatus.REJECTED
        this.rejectionReason = rejectionReason.trim()
        processedByUserId = processorUserId
        processedAt = LocalDateTime.now()
    }

    private fun checkPending(action: String) {
        check(isPending) { "PENDING 상태의 지출만 ${action}할 수 있습니다. 현재 상태: $status" }
    }

    /** 증빙 파일 정보 묶음. 항상 네 값이 함께 바뀌므로 한 덩어리로 넘긴다. */
    data class Evidence(
        val type: EvidenceType,
        val storageKey: String,
        val mimeType: String,
        val fileSize: Long?,
    ) {
        init {
            require(storageKey.isNotBlank()) { "증빙 저장 키는 비어 있을 수 없습니다." }
            FileUploadPolicy.EVIDENCE_IMAGE.validate(mimeType, fileSize)
        }
    }

    companion object {

        fun create(
            eventId: Long,
            applicantUserId: Long,
            amount: Long,
            spentOn: LocalDate,
            reason: String,
            tag: SpendingTag,
            otherDetail: String?,
            evidence: Evidence,
        ): Spending {
            require(eventId > 0) { "행사 ID는 양수여야 합니다." }
            require(applicantUserId > 0) { "신청자 ID는 양수여야 합니다." }
            validate(amount, reason, tag, otherDetail)

            return Spending(
                eventId = eventId,
                applicantUserId = applicantUserId,
                amount = amount,
                spentOn = spentOn,
                reason = reason.trim(),
                tag = tag,
                otherDetail = normalizeOtherDetail(tag, otherDetail),
                evidence = evidence,
            )
        }

        private fun validate(amount: Long, reason: String, tag: SpendingTag, otherDetail: String?) {
            require(amount > 0) { "지출 금액은 0원보다 커야 합니다." }
            require(reason.isNotBlank()) { "지출 사유는 비어 있을 수 없습니다." }
            require(tag != SpendingTag.OTHER || !otherDetail.isNullOrBlank()) {
                "기타 태그를 선택하면 상세 내용을 입력해야 합니다."
            }
        }

        /** OTHER가 아닌 태그에는 상세 내용을 남기지 않는다. 태그를 바꿔 수정할 때 옛 값이 남는 걸 막는다. */
        private fun normalizeOtherDetail(tag: SpendingTag, otherDetail: String?): String? =
            if (tag == SpendingTag.OTHER) otherDetail?.trim() else null
    }
}
