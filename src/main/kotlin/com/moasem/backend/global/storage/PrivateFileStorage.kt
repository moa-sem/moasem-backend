package com.moasem.backend.global.storage

import java.time.Duration

/**
 * 비공개 파일의 업로드·조회를 presigned URL로 중개한다.
 *
 * 파일 바이트가 서버를 거치지 않는 것이 이 포트의 존재 이유다. 클라이언트는 발급받은 URL로
 * 저장소에 직접 올리고 직접 내려받는다. 서버는 "누가 무엇에 접근해도 되는가"만 판단한다.
 *
 * **발급된 URL은 그 자체가 통행증이다.** 발급 전에 권한 검증(모임 구성원 여부 등)을 끝내야 하고,
 * [key]에 클라이언트가 보낸 값을 그대로 넘겨서는 안 된다. 저장 키는 항상 서버가 만들어
 * DB에 보관한 값을 쓴다. 유효 기간은 짧게 유지한다.
 *
 * 증빙 이미지 외의 비공개 파일도 같은 인터페이스를 쓴다.
 */
interface PrivateFileStorage {

    /**
     * 지정한 키로 파일을 올릴 수 있는 URL을 발급한다.
     *
     * [contentType]과 [contentLength]는 발급된 URL에 묶인다. 클라이언트가 다른 형식이나
     * 다른 크기로 올리려 하면 저장소가 거부한다. 서버를 거치지 않는 업로드에서 형식·용량 제한을
     * 강제할 수 있는 지점이 여기뿐이므로, 호출 전에 [FileUploadPolicy]로 값을 검증한다.
     */
    fun issueUploadUrl(key: String, contentType: String, contentLength: Long, expiry: Duration): String

    /** 지정한 키의 파일을 내려받을 수 있는 URL을 발급한다. */
    fun issueDownloadUrl(key: String, expiry: Duration): String
}
