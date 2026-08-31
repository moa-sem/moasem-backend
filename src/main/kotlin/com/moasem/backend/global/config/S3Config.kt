package com.moasem.backend.global.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3 클라이언트 구성.
 *
 * 자격증명은 [DefaultCredentialsProvider]가 환경변수, 프로파일, EC2 IAM 역할 순으로 찾는다.
 * 코드나 설정 파일에 키를 두지 않기 위해서다.
 */
@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config(
    private val properties: S3Properties,
) {

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()

    /** presigned URL 발급 전용. 다운로드 링크를 만들 때 쓴다. */
    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()
}
