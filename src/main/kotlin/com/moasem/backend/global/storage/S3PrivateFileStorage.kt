package com.moasem.backend.global.storage

import com.moasem.backend.global.config.S3Properties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

/**
 * 비공개 파일을 S3 presigned URL로 중개한다.
 *
 * 파일 바이트가 서버를 거치지 않는다. 증빙 이미지는 사용자가 휴대폰에서 바로 올리는
 * 수 MB짜리 사진이라, 서버가 받아 다시 올리면 대역폭과 메모리를 그대로 두 번 쓴다.
 *
 * 로컬에서는 AWS 자격증명 없이 개발할 수 있도록 [LocalPrivateFileStorage]가 대신 쓰인다.
 */
@Profile("!local")
@Component
class S3PrivateFileStorage(
    private val s3Presigner: S3Presigner,
    private val properties: S3Properties,
) : PrivateFileStorage {

    /**
     * 형식과 크기를 URL 자체에 묶는다.
     *
     * 서명에 포함된 값이라 클라이언트가 다른 Content-Type이나 다른 크기로 올리려 하면
     * S3가 거부한다. 업로드가 서버를 거치지 않는 이상, 형식·용량 제한을 강제할 수 있는
     * 지점은 여기뿐이다.
     */
    override fun issueUploadUrl(key: String, contentType: String, contentLength: Long, expiry: Duration): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm()
    }

    override fun issueDownloadUrl(key: String, expiry: Duration): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm()
    }
}
