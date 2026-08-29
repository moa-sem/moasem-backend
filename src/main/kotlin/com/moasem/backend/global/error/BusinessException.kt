package com.moasem.backend.global.error

/**
 * 비즈니스 규칙 위반을 나타내는 예외.
 *
 * [GlobalExceptionHandler]가 [errorCode]에 정의된 상태 코드와 메시지로 변환한다.
 * 규칙 위반이 아닌 프로그래밍 오류에는 사용하지 않는다.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)
