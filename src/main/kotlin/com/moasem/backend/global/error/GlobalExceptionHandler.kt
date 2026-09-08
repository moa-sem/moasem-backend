package com.moasem.backend.global.error

import com.moasem.backend.global.response.ApiResponse
// 요청 본문 파싱은 Spring이 쓰는 Jackson 3다. Hibernate의 JSONB 매핑이 쓰는 Jackson 2와 다르다.
import tools.jackson.databind.DatabindException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

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

    /** 필수 헤더 누락. 클라이언트 실수이므로 500이 아니라 400으로 알려야 한다. */
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.MISSING_REQUEST_PARAMETER, "'${e.headerName}' 헤더가 필요합니다.")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message)

    /**
     * 요청 본문을 읽지 못한 경우. 깨진 JSON, 인코딩 불일치, 타입이 맞지 않는 값 등이다.
     *
     * 서버 잘못이 아니라 요청이 잘못된 것이므로 400으로 알린다. 500으로 내려가면 프론트가
     * 재시도할 오류로 오해하고, 서버 로그에도 장애처럼 쌓인다.
     *
     * 원인 메시지는 그대로 내리지 않는다. 파싱 오류 메시지에는 요청 본문 일부와 내부
     * 클래스명이 섞여 나온다. 어느 필드가 문제인지까지만 알려준다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("요청 본문 파싱 실패", e)
        val field = unreadableField(e)
        val message = if (field != null) {
            "'$field' 값의 형식이 올바르지 않습니다."
        } else {
            ErrorCode.MALFORMED_REQUEST_BODY.message
        }
        return respond(ErrorCode.MALFORMED_REQUEST_BODY, message)
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResponse<Unit>> =
        respond(
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "'${e.contentType}'는 지원하지 않습니다. application/json으로 보내주세요.",
        )

    /** 존재하지 않는 경로. 오타난 URL에 500을 주면 장애로 오인된다. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> =
        respond(ErrorCode.ENTITY_NOT_FOUND, "존재하지 않는 경로입니다.")

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

    /**
     * 파싱 실패 지점의 필드명을 뽑는다.
     *
     * Jackson이 어느 속성에서 실패했는지 경로로 알려주는 경우에만 쓴다. 본문 자체가 깨졌거나
     * 경로 정보가 없으면 null을 돌려주고 일반 메시지를 쓴다.
     */
    private fun unreadableField(e: HttpMessageNotReadableException): String? {
        val cause = e.cause
        if (cause !is DatabindException) return null
        return cause.path.mapNotNull { it.propertyName }.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    private fun respond(errorCode: ErrorCode, message: String): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(errorCode.status).body(ApiResponse.error(errorCode, message))
}
