package com.moasem.backend.domain.report.service.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

class LocalReportFileStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private fun storage() = LocalReportFileStorage(tempDir.toString(), BASE_URL)

    @Test
    fun `저장한 파일을 그대로 다시 읽는다`() {
        val storage = storage()
        val content = "보고서 내용".toByteArray()

        val key = storage.upload(PDF_KEY, content, "application/pdf")

        assertThat(key).isEqualTo(PDF_KEY)
        assertThat(storage.read(PDF_KEY)).isEqualTo(content)
    }

    @Test
    @DisplayName("key에 하위 경로가 있으면 디렉터리를 만들어 저장한다")
    fun createsNestedDirectories() {
        val storage = storage()

        storage.upload("reports/7/nested/report.csv", "csv".toByteArray(), "text/csv")

        assertThat(tempDir.resolve("reports/7/nested/report.csv")).exists()
    }

    @Test
    fun `저장되지 않은 key를 읽으면 null`() {
        assertThat(storage().read("reports/999/report.pdf")).isNull()
    }

    @Test
    fun `다운로드 URL은 서빙 경로에 key를 붙인 형태다`() {
        val url = storage().generateDownloadUrl(PDF_KEY, Duration.ofMinutes(5))

        assertThat(url).isEqualTo("$BASE_URL/api/v1/dev/files/$PDF_KEY")
    }

    @Test
    @DisplayName("저장소 밖을 가리키는 key는 거부한다")
    fun rejectsPathTraversal() {
        // 서빙 시 key가 URL에서 그대로 넘어오므로, 상위 경로를 타고 나가지 못하게 막아야 한다.
        assertThatThrownBy { storage().read("../../etc/passwd") }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy { storage().upload("reports/../../evil.pdf", ByteArray(0), "application/pdf") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    companion object {
        private const val BASE_URL = "http://localhost:8080"
        private const val PDF_KEY = "reports/1/report.pdf"
    }
}
