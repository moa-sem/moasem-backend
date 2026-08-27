package com.moasem.backend.domain.spending.entity

/**
 * 지출 태그. 기획안에 고정된 6종만 사용하고 사용자가 임의로 추가할 수 없다.
 *
 * [OTHER]를 고르면 `Spending.otherDetail`이 필수다.
 */
enum class SpendingTag(val label: String) {
    MEAL("식비"),
    ACCOMMODATION("숙박비"),
    TRANSPORTATION("교통비"),
    VENUE("대관비"),
    SUPPLIES("물품비"),
    OTHER("기타"),
}
