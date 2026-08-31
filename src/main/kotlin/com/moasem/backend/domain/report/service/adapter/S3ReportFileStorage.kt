package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.report.service.port.ReportFileStorage
import com.moasem.backend.global.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * 보고서 파일을 S3에 저장하고 다운로드 링크를 발급한다.
 *
 * 다운로드는 서버가 파일을 중계하지 않고 presigned URL을 내려 앱이 S3에서 직접 받는다.
 * 서버 메모리·대역폭을 쓰지 않고, 모바일에서 자주 발생하는 연결 끊김 재개도 S3가 처리한다.
 */
@Component
class S3ReportFileStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3Properties,
) : ReportFileStorage {

    override fun upload(key: String, content: ByteArray, contentType: String): String {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(content.size.toLong())
            .build()

        s3Client.putObject(request, RequestBody.fromBytes(content))
        return key
    }

    /**
     * 발급된 URL은 그 자체가 통행증이다. 유효 기간 동안은 링크를 아는 누구나 내려받을 수 있으므로
     * 호출 전에 반드시 행사 마감 여부와 모임 구성원 여부를 검증해야 한다.
     */
    override fun generateDownloadUrl(key: String, expiry: Duration): String {
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
