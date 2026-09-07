package com.moasem.backend.domain.group.repository

import com.moasem.backend.domain.auth.entity.User
import com.moasem.backend.domain.group.entity.Group
import com.moasem.backend.domain.group.entity.GroupMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    fun findByGroup(group: Group): MutableList<GroupMember>

    fun countByGroup(group: Group): Long

    fun existsByGroupAndUser(group: Group, user: User): Boolean

    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean

    /**
     * 구성원과 사용자를 한 번에 읽는다.
     *
     * 이름까지 함께 쓰는 호출부(결산 스냅샷)가 있어 join fetch로 가져온다.
     * 지연 로딩에 맡기면 구성원 수만큼 조회가 나간다.
     */
    @Query("""
        SELECT gm
        FROM GroupMember gm
        JOIN FETCH gm.user
        WHERE gm.group.id = :groupId
    """)
    fun findAllWithUserByGroupId(@Param("groupId") groupId: Long): List<GroupMember>

    @Query("""
        SELECT gm.group
        FROM GroupMember gm
        WHERE gm.user = :user AND gm.group.groupStatus = com.moasem.backend.domain.group.entity.GroupStatus.ACTIVE
    """)
    fun findActiveGroupByUser(@Param("user") user: User): List<Group>
}