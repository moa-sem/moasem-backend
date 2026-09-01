package com.moasem.backend.domain.spending.repository

import com.moasem.backend.domain.spending.entity.Spending
import com.moasem.backend.domain.spending.entity.SpendingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface SpendingRepository : JpaRepository<Spending, Long> {

    /** 경로의 행사와 지출이 실제로 이어져 있는지까지 한 번에 확인한다. */
    fun findByIdAndEventId(id: Long, eventId: Long): Spending?

    /**
     * 승인·반려 처리용 조회. 대상 행을 잠근 채 읽는다(`SELECT ... FOR UPDATE`).
     *
     * 같은 지출에 승인과 반려가 동시에 들어오면 두 트랜잭션 모두 PENDING을 보고 각자 처리해버린다.
     * 뒤 트랜잭션이 앞 트랜잭션의 커밋을 기다렸다가 갱신된 상태를 다시 읽게 해서 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByIdAndEventId(id: Long, eventId: Long): Spending?

    fun findAllByEventId(eventId: Long, pageable: Pageable): Page<Spending>

    fun findAllByEventIdAndStatus(eventId: Long, status: SpendingStatus, pageable: Pageable): Page<Spending>
}
