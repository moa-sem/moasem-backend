package com.moasem.backend.domain.spending.converter

import com.moasem.backend.domain.spending.dto.SpendingDetailResponse
import com.moasem.backend.domain.spending.dto.SpendingListResponse
import com.moasem.backend.domain.spending.entity.Spending

object SpendingConverter {

    /**
     * 상세 응답에는 [Spending.evidenceStorageKey]를 담지 않는다.
     *
     * 저장 키가 노출되면 증빙 조회 API의 권한 검증을 우회할 실마리가 된다.
     * 증빙 이미지는 별도 URL 발급 API로만 접근한다.
     */
    fun toDetailResponse(spending: Spending): SpendingDetailResponse = SpendingDetailResponse(
        spendingId = spending.id ?: error("저장되지 않은 지출은 응답으로 변환할 수 없습니다."),
        eventId = spending.eventId,
        applicantUserId = spending.applicantUserId,
        amount = spending.amount,
        spentOn = spending.spentOn,
        reason = spending.reason,
        tag = spending.tag,
        tagLabel = spending.tag.label,
        otherDetail = spending.otherDetail,
        evidenceType = spending.evidenceType,
        status = spending.status,
        processedByUserId = spending.processedByUserId,
        rejectionReason = spending.rejectionReason,
        processedAt = spending.processedAt,
        createdAt = spending.createdAt,
    )

    fun toListResponse(spending: Spending): SpendingListResponse = SpendingListResponse(
        spendingId = spending.id ?: error("저장되지 않은 지출은 응답으로 변환할 수 없습니다."),
        applicantUserId = spending.applicantUserId,
        amount = spending.amount,
        spentOn = spending.spentOn,
        reason = spending.reason,
        tag = spending.tag,
        tagLabel = spending.tag.label,
        status = spending.status,
        createdAt = spending.createdAt,
    )
}
