package com.moasem.backend.global.error

import com.moasem.backend.global.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 모든 컨트롤러에서 발생한 예외를 공통 응답 형태로 변환한다.
 *
 * 컨트롤러가 try-catch로 예외를 감싸지 않도록 하는 것이 목적이다.
 * 서비스는 [BusinessException]을 던지기만 하면 된다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("비즈니스 예외: {} - {}", e.errorCode.code, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode, e.message))
    }

    /** @Valid 검증 실패. 어느 필드가 왜 틀렸는지 함께 내려줘야 프론트가 폼에 표시할 수 있다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val errors = e.bindingResult.fieldErrors.map {
            ApiResponse.FieldError(
                field = it.field,
                value = it.rejectedValue?.toString(),
                reason = it.defaultMessage ?: ErrorCode.INVALID_INPUT_VALUE.message,
            )
        }
        log.debug("검증 실패: {}", errors)
        return ResponseEntity
            .status(ErrorCode.INVALID_INPUT_VALUE.status)
            .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errors))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.INVALID_TYPE_VALUE, "'${e.name}' 파라미터의 형식이 올바르지 않습니다.")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.MISSING_REQUEST_PARAMETER, "'${e.parameterName}' 파라미터가 필요합니다.")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(e: NoSuchElementException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("리소스 없음: {}", e.message)
        return respond(ErrorCode.ENTITY_NOT_FOUND, e.message ?: ErrorCode.ENTITY_NOT_FOUND.message)
    }

    /**
     * 처리하지 못한 예외.
     *
     * 원인 메시지를 클라이언트에 그대로 내리지 않는다. 내부 구조나 스택이 노출될 수 있어
     * 고정된 메시지만 주고 실제 원인은 로그로 남긴다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("처리되지 않은 예외", e)
        return respond(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.message)
    }

    private fun respond(errorCode: ErrorCode, message: String): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(errorCode.status).body(ApiResponse.error(errorCode, message))
}
