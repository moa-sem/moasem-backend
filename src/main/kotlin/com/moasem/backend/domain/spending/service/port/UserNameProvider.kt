package com.moasem.backend.domain.spending.service.port

/**
 * 사용자 ID를 표시용 이름으로 바꾸는 경계다.
 *
 * 지출 목록에는 신청자가 이름으로 찍히는데, spending은 사용자를 ID로만 들고 있다.
 * auth 도메인에서 이 인터페이스의 어댑터를 제공한다.
 */
interface UserNameProvider {

    /**
     * 여러 사용자의 이름을 한 번에 조회한다.
     *
     * 한 명씩 받지 않는 건 목록 한 페이지에 지출이 스무 건이기 때문이다.
     * 낱개로 조회하면 그만큼 쿼리가 나간다.
     *
     * @return ID → 이름. **없는 사용자는 결과에서 빠진다.** 탈퇴한 사용자의 지출이 남아 있을 수
     *   있어 조회 실패를 예외로 다루지 않는다. 없는 이름을 어떻게 표시할지는 호출부가 정한다.
     */
    fun findNames(userIds: Collection<Long>): Map<Long, String>
}
