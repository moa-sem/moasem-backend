package com.moasem.backend.domain.report.service.port

import java.time.Duration

/**
 * 테스트용 [ReportFileStorage].
 *
 * 실제 S3에 접근하지 않고 메모리에만 보관한다. 저장된 바이트를 다시 꺼내볼 수 있어
 * 생성된 PDF·CSV 내용까지 검증할 수 있다.
 */
class FakeReportFileStorage : ReportFileStorage {

    private val files = mutableMapOf<String, StoredFile>()

    override fun upload(key: String, content: ByteArray, contentType: String): String {
        files[key] = StoredFile(content, contentType)
        return key
    }

    override fun generateDownloadUrl(key: String, expiry: Duration): String {
        require(files.containsKey(key)) { "저장되지 않은 파일의 URL은 발급할 수 없습니다. key=$key" }
        return "https://fake-storage.test/$key?expires=${expiry.seconds}"
    }

    fun read(key: String): StoredFile? = files[key]

    fun storedKeys(): Set<String> = files.keys.toSet()

    data class StoredFile(val content: ByteArray, val contentType: String) {
        // ByteArray는 equals가 참조 비교라 data class 기본 구현을 쓸 수 없다.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredFile) return false
            return contentType == other.contentType && content.contentEquals(other.content)
        }

        override fun hashCode(): Int = 31 * content.contentHashCode() + contentType.hashCode()
    }
}
