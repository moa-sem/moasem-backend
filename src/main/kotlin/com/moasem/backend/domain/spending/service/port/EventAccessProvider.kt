package com.moasem.backend.domain.spending.service.port

/**
 * spending 도메인이 event 도메인의 구현체에 직접 의존하지 않기 위한 조회 경계다.
 * event 도메인에서 이 인터페이스의 어댑터를 제공한다.
 */
interface EventAccessProvider {

    /** 행사가 없으면 null을 돌려준다. */
    fun findAccess(eventId: Long): EventAccess?
}

/**
 * 지출 검증에 필요한 행사 정보만 추린 값.
 *
 * spending은 행사의 예산이나 기간을 보지 않는다. "어느 모임의 행사인가"와
 * "아직 지출을 받는 상태인가"만 알면 된다.
 */
data class EventAccess(
    val eventId: Long,
    val groupId: Long,
    val isActive: Boolean,
)
