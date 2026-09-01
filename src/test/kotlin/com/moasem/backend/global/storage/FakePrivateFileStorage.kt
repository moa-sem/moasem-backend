package com.moasem.backend.global.storage

import java.time.Duration

/**
 * 테스트용 [PrivateFileStorage].
 *
 * 실제 S3에 접근하지 않고, 어떤 키에 어떤 조건으로 URL을 발급했는지만 기록한다.
 * 서비스 테스트에서 "권한 검증을 통과한 뒤에 URL이 발급됐는지"를 확인하는 용도다.
 */
class FakePrivateFileStorage : PrivateFileStorage {

    val issuedUploads = mutableListOf<IssuedUpload>()
    val issuedDownloadKeys = mutableListOf<String>()

    override fun issueUploadUrl(key: String, contentType: String, contentLength: Long, expiry: Duration): String {
        validateKey(key)
        issuedUploads += IssuedUpload(key, contentType, contentLength, expiry)
        return "https://fake-storage.test/$key?method=PUT&contentType=$contentType&expires=${expiry.seconds}"
    }

    override fun issueDownloadUrl(key: String, expiry: Duration): String {
        validateKey(key)
        issuedDownloadKeys += key
        return "https://fake-storage.test/$key?method=GET&expires=${expiry.seconds}"
    }

    private fun validateKey(key: String) {
        require(key.isNotBlank()) { "저장 키는 비어 있을 수 없습니다." }
    }

    data class IssuedUpload(
        val key: String,
        val contentType: String,
        val contentLength: Long,
        val expiry: Duration,
    )
}
