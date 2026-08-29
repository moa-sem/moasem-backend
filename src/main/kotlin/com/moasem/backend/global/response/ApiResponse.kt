package com.moasem.backend.global.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.moasem.backend.global.error.ErrorCode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 모든 API 응답을 감싸는 공통 응답 객체.
 *
 * 값이 없는 필드는 응답에서 생략된다. 성공 응답마다 `"errors": null`이 붙는 것을 막기 위해서다.
 */
@Schema(description = "공통 API 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    @field:Schema(description = "요청 성공 여부", example = "true")
    val success: Boolean,

    @field:Schema(description = "응답 코드", example = "OK")
    val code: String,

    @field:Schema(description = "응답 메시지", example = "요청이 성공했습니다.")
    val message: String,

    @field:Schema(description = "응답 데이터")
    val data: T? = null,

    @field:Schema(description = "필드 단위 검증 오류 목록")
    val errors: List<FieldError>? = null,

    @field:Schema(description = "응답 생성 시각")
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {

    @Schema(description = "필드 검증 오류")
    data class FieldError(
        @field:Schema(description = "오류 필드명", example = "title")
        val field: String,

        @field:Schema(description = "거부된 값", example = "")
        val value: String?,

        @field:Schema(description = "오류 사유", example = "행사 제목은 비어 있을 수 없습니다.")
        val reason: String,
    )

    companion object {
        private const val SUCCESS_CODE = "OK"
        private const val SUCCESS_MESSAGE = "요청이 성공했습니다."

        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(success = true, code = SUCCESS_CODE, message = SUCCESS_MESSAGE, data = data)

        fun <T> success(message: String, data: T): ApiResponse<T> =
            ApiResponse(success = true, code = SUCCESS_CODE, message = message, data = data)

        /** 반환할 데이터가 없는 성공 응답. 생성·삭제 등에 사용한다. */
        fun ok(): ApiResponse<Unit> =
            ApiResponse(success = true, code = SUCCESS_CODE, message = SUCCESS_MESSAGE)

        fun error(errorCode: ErrorCode): ApiResponse<Unit> =
            ApiResponse(success = false, code = errorCode.code, message = errorCode.message)

        fun error(errorCode: ErrorCode, message: String): ApiResponse<Unit> =
            ApiResponse(success = false, code = errorCode.code, message = message)

        fun error(errorCode: ErrorCode, errors: List<FieldError>): ApiResponse<Unit> =
            ApiResponse(
                success = false,
                code = errorCode.code,
                message = errorCode.message,
                errors = errors,
            )
    }
}
