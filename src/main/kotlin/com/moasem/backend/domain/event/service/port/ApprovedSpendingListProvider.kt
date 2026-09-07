package com.moasem.backend.domain.event.service.port

import java.time.LocalDate

/**
 * 행사의 승인된 지출을 한 건씩 제공하는 경계다.
 *
 * 합계만 주는 [ApprovedSpendingTotalProvider]와 나눠 둔다. 합계는 마감 화면이 매번 쓰는
 * 값이라 DB에서 SUM으로 끝내는 편이 싸고, 개별 내역은 결산 보고서를 만들 때만 필요하다.
 *
 * PENDING·REJECTED를 걸러내는 책임은 spending 어댑터에 있다. 받는 쪽은 상태를 다시
 * 확인하지 않는다. 규칙이 두 곳으로 흩어지면 한쪽만 고쳐졌을 때 금액이 어긋난다.
 */
interface ApprovedSpendingListProvider {

    /** 승인된 지출이 없으면 빈 목록. 지출일 오름차순으로, 같은 날이면 등록순으로 준다. */
    fun getApprovedSpendings(eventId: Long): List<ApprovedSpendingDetail>
}

/**
 * 결산 표에 필요한 지출 한 건.
 *
 * 신청자는 ID로만 준다. 이름은 auth 도메인이 가진 값이고, spending은 그걸 알지 못한다.
 */
data class ApprovedSpendingDetail(
    val spendingId: Long,
    val applicantUserId: Long,
    val amount: Long,
    val spentOn: LocalDate,
    /** DB 저장 코드. 태그별 집계 키로 쓴다. */
    val tag: String,
    /**
     * 태그의 표시용 한글 라벨.
     *
     * 태그 분류 체계는 spending이 소유하므로 라벨도 여기서 함께 넘긴다.
     * 받는 쪽이 자체 매핑을 들고 있으면 라벨이 바뀌었을 때 조용히 어긋난다.
     */
    val tagLabel: String,
    val description: String,
    /** 기타 태그일 때만 채워지는 상세 내용. */
    val otherDetail: String?,
    /**
     * 증빙 파일의 저장소 키.
     *
     * 발급된 URL이 아니라 키를 넘긴다. presigned URL은 수 분 뒤 만료되므로 결산 스냅샷처럼
     * 영구 보관되는 곳에 박히면 죽은 링크가 된다(#92).
     */
    val evidenceStorageKey: String,
)
