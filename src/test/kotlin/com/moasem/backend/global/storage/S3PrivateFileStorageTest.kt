package com.moasem.backend.global.storage

import com.moasem.backend.global.config.S3Properties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * 실제 S3에 붙지 않는다. 붙이면 테스트가 네트워크와 자격증명, 비용에 의존하게 된다.
 * 여기서 확인할 것은 SDK에 올바른 요청을 만들어 넘기는지다.
 *
 * 업로드 쪽이 특히 중요하다. 파일이 서버를 거치지 않으므로 형식·용량 제한을 강제할 수 있는
 * 지점은 "서명에 무엇을 묶었는가" 하나뿐이다. contentType이나 contentLength가 요청에서
 * 빠지면 제한이 사라지고, 그래도 업로드는 성공하기 때문에 겉으로는 아무 문제가 없어 보인다.
 */
class S3PrivateFileStorageTest {

    private val s3Presigner = mockk<S3Presigner>()
    private val properties = S3Properties(bucket = BUCKET, region = "ap-northeast-2")
    private val storage = S3PrivateFileStorage(s3Presigner, properties)

    @Test
    @DisplayName("업로드 URL에 형식과 크기를 함께 묶는다")
    fun bindsContentTypeAndLengthToUploadUrl() {
        val request = slot<PutObjectPresignRequest>()
        every { s3Presigner.presignPutObject(capture(request)) } returns presignedPut()

        val url = storage.issueUploadUrl(KEY, "image/jpeg", 204_800L, Duration.ofMinutes(5))

        assertThat(url).startsWith("https://")
        with(request.captured.putObjectRequest()) {
            assertThat(bucket()).isEqualTo(BUCKET)
            assertThat(key()).isEqualTo(KEY)
            assertThat(contentType()).isEqualTo("image/jpeg")
            assertThat(contentLength()).isEqualTo(204_800L)
        }
    }

    @Test
    @DisplayName("업로드 URL의 만료 시간은 호출부가 정한다")
    fun uploadExpiryComesFromCaller() {
        val request = slot<PutObjectPresignRequest>()
        every { s3Presigner.presignPutObject(capture(request)) } returns presignedPut()

        storage.issueUploadUrl(KEY, "image/png", 1_024L, Duration.ofSeconds(30))

        assertThat(request.captured.signatureDuration()).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    @DisplayName("조회 URL은 지정한 버킷과 키로 발급한다")
    fun generatesDownloadUrl() {
        val request = slot<GetObjectPresignRequest>()
        every { s3Presigner.presignGetObject(capture(request)) } returns presignedGet()

        val url = storage.issueDownloadUrl(KEY, Duration.ofMinutes(5))

        assertThat(url).startsWith("https://")
        assertThat(request.captured.signatureDuration()).isEqualTo(Duration.ofMinutes(5))
        assertThat(request.captured.getObjectRequest().bucket()).isEqualTo(BUCKET)
        assertThat(request.captured.getObjectRequest().key()).isEqualTo(KEY)
    }

    /**
     * 조회 URL에는 형식이나 크기가 묶이지 않는다. 읽기라 제한할 값이 없고,
     * 묶으면 저장된 파일과 다를 때 오히려 열리지 않는다.
     */
    @Test
    @DisplayName("업로드와 조회는 서로 다른 서명 요청을 쓴다")
    fun uploadAndDownloadUseDifferentRequests() {
        val putRequest = slot<PutObjectPresignRequest>()
        val getRequest = slot<GetObjectPresignRequest>()
        every { s3Presigner.presignPutObject(capture(putRequest)) } returns presignedPut()
        every { s3Presigner.presignGetObject(capture(getRequest)) } returns presignedGet()

        storage.issueUploadUrl(KEY, "image/jpeg", 100L, Duration.ofMinutes(5))
        storage.issueDownloadUrl(KEY, Duration.ofMinutes(5))

        assertThat(putRequest.captured.putObjectRequest().contentType()).isEqualTo("image/jpeg")
        assertThat(getRequest.captured.getObjectRequest().key()).isEqualTo(KEY)
    }

    private fun presignedPut() = mockk<PresignedPutObjectRequest>().also {
        every { it.url() } returns URI.create("https://s3.example/$KEY?sig=put").toURL()
    }

    private fun presignedGet() = mockk<PresignedGetObjectRequest>().also {
        every { it.url() } returns URI.create("https://s3.example/$KEY?sig=get").toURL()
    }

    companion object {
        private const val BUCKET = "moasem-backend-storage"
        private const val KEY = "spendings/1/42/evidence.jpg"
    }
}
