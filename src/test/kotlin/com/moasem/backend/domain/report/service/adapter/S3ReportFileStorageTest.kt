package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.global.config.S3Properties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.net.URI
import java.time.Duration

/**
 * 실제 S3에 붙지 않는다. 붙이면 테스트가 네트워크와 자격증명, 비용에 의존하게 된다.
 * 여기서 확인할 것은 SDK에 올바른 요청을 만들어 넘기는지다.
 */
class S3ReportFileStorageTest {

    private val s3Client = mockk<S3Client>()
    private val s3Presigner = mockk<S3Presigner>()
    private val properties = S3Properties(bucket = BUCKET, region = "ap-northeast-2")
    private val storage = S3ReportFileStorage(s3Client, s3Presigner, properties)

    @Test
    @DisplayName("지정한 버킷과 key로 업로드한다")
    fun uploadsToConfiguredBucket() {
        val request = slot<PutObjectRequest>()
        every {
            s3Client.putObject(capture(request), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        val content = "결산 보고서".toByteArray()
        val key = storage.upload("reports/1/report.pdf", content, "application/pdf")

        assertThat(key).isEqualTo("reports/1/report.pdf")
        assertThat(request.captured.bucket()).isEqualTo(BUCKET)
        assertThat(request.captured.key()).isEqualTo("reports/1/report.pdf")
        assertThat(request.captured.contentType()).isEqualTo("application/pdf")
        assertThat(request.captured.contentLength()).isEqualTo(content.size.toLong())
    }

    @Test
    @DisplayName("업로드 본문이 그대로 전달된다")
    fun passesContentThrough() {
        val body = slot<RequestBody>()
        every { s3Client.putObject(any<PutObjectRequest>(), capture(body)) } returns
            PutObjectResponse.builder().build()

        val content = "a,b,c".toByteArray()
        storage.upload("reports/1/report.csv", content, "text/csv")

        assertThat(body.captured.optionalContentLength().orElse(0)).isEqualTo(content.size.toLong())
    }

    @Test
    @DisplayName("요청한 만료 시간으로 presigned URL을 발급한다")
    fun generatesPresignedUrlWithGivenExpiry() {
        val request = slot<GetObjectPresignRequest>()
        val presigned = mockk<PresignedGetObjectRequest>()
        every { presigned.url() } returns URI.create("https://s3.example/reports/1/report.pdf?sig=x").toURL()
        every { s3Presigner.presignGetObject(capture(request)) } returns presigned

        val url = storage.generateDownloadUrl("reports/1/report.pdf", Duration.ofMinutes(5))

        assertThat(url).startsWith("https://")
        assertThat(request.captured.signatureDuration()).isEqualTo(Duration.ofMinutes(5))
        assertThat(request.captured.getObjectRequest().bucket()).isEqualTo(BUCKET)
        assertThat(request.captured.getObjectRequest().key()).isEqualTo("reports/1/report.pdf")
    }

    @Test
    @DisplayName("만료 시간이 짧게 유지되는지는 호출부가 정한다")
    fun expiryComesFromCaller() {
        val request = slot<GetObjectPresignRequest>()
        val presigned = mockk<PresignedGetObjectRequest>()
        every { presigned.url() } returns URI.create("https://s3.example/x").toURL()
        every { s3Presigner.presignGetObject(capture(request)) } returns presigned

        storage.generateDownloadUrl("reports/2/report.csv", Duration.ofSeconds(30))

        assertThat(request.captured.signatureDuration()).isEqualTo(Duration.ofSeconds(30))
        verify(exactly = 1) { s3Presigner.presignGetObject(any<GetObjectPresignRequest>()) }
    }

    companion object {
        private const val BUCKET = "moasem-backend-storage"
    }
}
