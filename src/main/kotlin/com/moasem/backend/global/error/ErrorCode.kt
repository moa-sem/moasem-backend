package com.moasem.backend.global.error

import org.springframework.http.HttpStatus

/**
 * 애플리케이션 전역 에러 코드.
 *
 * 클라이언트에 내려가는 [code]는 enum 이름을 그대로 쓴다. 별도 API 명세 문서 없이
 * Swagger만 사용하기로 했으므로, 번호 체계를 쓰면 프론트가 코드의 의미를 확인할 방법이
 * 백엔드 소스밖에 없어진다.
 *
 * 각 도메인 담당자가 자기 도메인 코드를 이 파일에 추가한다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "요청 파라미터 타입이 올바르지 않습니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 누락되었습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 파일 업로드
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용하지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "허용 용량을 넘는 파일입니다."),

    // 인증·인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 모임
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "모임 구성원을 찾을 수 없습니다."),
    ALREADY_GROUP_MEMBER(HttpStatus.CONFLICT, "이미 가입된 모임입니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "모임 구성원이 아닙니다."),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "모임장만 수행할 수 있습니다."),

    // 행사
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."),
    EVENT_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 마감된 행사입니다."),
    EVENT_NOT_CLOSED(HttpStatus.CONFLICT, "마감되지 않은 행사입니다."),

    // 지출
    SPENDING_NOT_FOUND(HttpStatus.NOT_FOUND, "지출 내역을 찾을 수 없습니다."),
    SPENDING_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 지출입니다."),
    NOT_SPENDING_APPLICANT(HttpStatus.FORBIDDEN, "본인이 신청한 지출만 수정할 수 있습니다."),
    INVALID_EVIDENCE_KEY(HttpStatus.FORBIDDEN, "본인이 발급받은 증빙 저장 키가 아닙니다."),

    // 보고서
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 보고서가 존재하는 행사입니다."),
    REPORT_GENERATING(HttpStatus.CONFLICT, "보고서를 생성하는 중입니다."),
    REPORT_NOT_DOWNLOADABLE(HttpStatus.CONFLICT, "아직 다운로드할 수 없는 보고서입니다."),
    REPORT_NOT_RETRYABLE(HttpStatus.CONFLICT, "재시도할 수 없는 상태입니다."),
    ;

    /** 클라이언트에 내려가는 코드. enum 이름과 항상 같다. */
    val code: String get() = name
}
