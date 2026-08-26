package com.moasem.backend.domain.event.service.port

/**
 * event 도메인이 group 도메인의 구현체에 직접 의존하지 않기 위한 조회 경계다.
 * 실제 Group·Membership 구현이 준비되면 group 도메인에서 이 인터페이스의 어댑터를 제공한다.
 */
interface GroupAccessProvider {

    fun existsGroup(groupId: Long): Boolean

    fun isMember(groupId: Long, userId: Long): Boolean

    fun isOwner(groupId: Long, userId: Long): Boolean
}
