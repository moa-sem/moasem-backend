package com.moasem.backend.global.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

class LocalPrivateFileStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private val storage: LocalPrivateFileStorage
        get() = LocalPrivateFileStorage(tempDir.toString(), BASE_URL)

    @Test
    @DisplayName("올린 파일을 그대로 다시 읽는다")
    fun writeThenRead() {
        val content = byteArrayOf(1, 2, 3)
        val storage = storage

        storage.write(KEY, content)

        assertThat(storage.read(KEY)).isEqualTo(content)
    }

    @Test
    @DisplayName("없는 키를 읽으면 null이다")
    fun readMissingKey() {
        assertThat(storage.read("spendings/1/42/none.jpg")).isNull()
    }

    @Test
    @DisplayName("업로드와 다운로드 URL은 같은 키를 가리킨다")
    fun urlsPointToSameKey() {
        val storage = storage
        val expected = "$BASE_URL${LocalPrivateFileStorage.PATH_PREFIX}$KEY"

        assertThat(storage.issueUploadUrl(KEY, "image/jpeg", 100L, EXPIRY)).isEqualTo(expected)
        assertThat(storage.issueDownloadUrl(KEY, EXPIRY)).isEqualTo(expected)
    }

    /**
     * 서빙 시 키는 URL에서 그대로 넘어온다. 막지 않으면 저장소 밖 파일을 읽거나 덮어쓸 수 있다.
     */
    @Test
    @DisplayName("저장소 밖을 가리키는 키는 거부한다")
    fun rejectsPathTraversal() {
        val storage = storage

        assertThatThrownBy { storage.read("../../etc/passwd") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("저장소 밖")

        assertThatThrownBy { storage.write("spendings/../../evil.jpg", byteArrayOf(0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("저장소 밖")
    }

    companion object {
        private const val BASE_URL = "http://localhost:8080"
        private const val KEY = "spendings/1/42/evidence.jpg"
        private val EXPIRY: Duration = Duration.ofMinutes(5)
    }
}
