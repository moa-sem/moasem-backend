package com.moasem.backend.domain.report.service.port

/**
 * 모임 구성원 여부를 확인하는 경계다.
 *
 * report 도메인이 group 도메인 구현체에 직접 의존하지 않도록 격리한다.
 * 실제 어댑터는 group 도메인 완성 후 제공한다.
 */
interface GroupMembershipProvider {

    fun isMember(groupId: Long, userId: Long): Boolean
}
