package com.moasem.backend.domain.spending.entity

/**
 * 지출 신청 상태.
 *
 * [PENDING]에서만 [APPROVED] 또는 [REJECTED]로 전이할 수 있고, 전이 후에는 되돌릴 수 없다.
 */
enum class SpendingStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
