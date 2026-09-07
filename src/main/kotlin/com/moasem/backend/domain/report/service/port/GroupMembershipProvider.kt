package com.moasem.backend.domain.report.service.port

/**
 * 모임 정보를 조회하는 경계다.
 *
 * report 도메인이 group 도메인 구현체에 직접 의존하지 않도록 격리한다.
 * 결산 스냅샷을 조립하는 쪽도 같은 경계를 쓴다. 모임 조회 통로를 둘로 나누면
 * "구성원이란 무엇인가"(탈퇴·비활성 모임 처리)가 두 곳으로 흩어진다.
 */
interface GroupMembershipProvider {

    /** OWNER, MEMBER 모두 구성원으로 본다. 비활성·삭제된 모임은 구성원이 없는 것으로 취급한다. */
    fun isMember(groupId: Long, userId: Long): Boolean

    /** 모임이 없으면 null. 표시용 이름이므로 모임 상태는 따지지 않는다. */
    fun findGroupName(groupId: Long): String?

    /** 모임이 없으면 빈 목록. */
    fun findMembers(groupId: Long): List<GroupMemberData>
}

/** 결산 표시에 필요한 구성원 정보만 추린 값. */
data class GroupMemberData(
    val userId: Long,
    val name: String,
    val isOwner: Boolean,
)
