package com.moasem.backend.global.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * [PrivateFileStorage] 구현이 지켜야 할 계약을 fake로 검증한다.
 *
 * 실제 S3 어댑터를 붙일 때도 같은 계약을 만족해야 한다.
 */
class PrivateFileStorageContractTest {

    private val storage = FakePrivateFileStorage()

    @Nested
    @DisplayName("업로드 URL 발급")
    inner class UploadUrl {

        @Test
        fun `요청한 형식과 크기를 URL 발급 조건으로 함께 묶는다`() {
            storage.issueUploadUrl(KEY, "image/jpeg", 2_048L, Duration.ofMinutes(5))

            val issued = storage.issuedUploads.single()
            assertThat(issued.key).isEqualTo(KEY)
            assertThat(issued.contentType).isEqualTo("image/jpeg")
            assertThat(issued.contentLength).isEqualTo(2_048L)
            assertThat(issued.expiry).isEqualTo(Duration.ofMinutes(5))
        }

        @Test
        fun `빈 키로는 발급할 수 없다`() {
            assertThatThrownBy { storage.issueUploadUrl("  ", "image/jpeg", 1L, Duration.ofMinutes(5)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("조회 URL 발급")
    inner class DownloadUrl {

        @Test
        fun `저장 키로 조회 URL을 발급한다`() {
            val url = storage.issueDownloadUrl(KEY, Duration.ofMinutes(5))

            assertThat(url).contains(KEY)
            assertThat(storage.issuedDownloadKeys).containsExactly(KEY)
        }

        @Test
        fun `빈 키로는 발급할 수 없다`() {
            assertThatThrownBy { storage.issueDownloadUrl("", Duration.ofMinutes(5)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    companion object {
        private const val KEY = "spendings/1/evidence.jpg"
    }
}
