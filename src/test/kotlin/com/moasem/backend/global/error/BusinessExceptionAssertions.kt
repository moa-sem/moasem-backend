package com.moasem.backend.global.error

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * [BusinessException]이 어떤 [ErrorCode]로 떨어졌는지까지 확인한다.
 *
 * 예외 타입만 보면 부족하다. 응답의 HTTP 상태와 코드는 [ErrorCode]가 정하므로,
 * 타입이 맞아도 코드가 틀리면 클라이언트는 다른 응답을 받는다.
 * 메시지 문구로 확인하는 것보다 문구 수정에 덜 깨지기도 한다.
 *
 * `assertThatThrownBy { }` 뒤에도, 잡아 둔 예외를 담은 컬렉션 단언 뒤에도 쓸 수 있도록
 * 두 단언의 공통 상위 타입에 붙인다.
 */
fun AbstractObjectAssert<*, *>.hasErrorCode(errorCode: ErrorCode) {
    isInstanceOfSatisfying(BusinessException::class.java) {
        assertThat(it.errorCode).isEqualTo(errorCode)
    }
}
