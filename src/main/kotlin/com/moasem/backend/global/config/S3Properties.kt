package com.moasem.backend.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3 접속 설정.
 *
 * 자격증명은 여기에 담지 않는다. AWS SDK가 기본 제공자 체인으로 찾는다
 * (EC2는 IAM 역할, 로컬은 환경변수나 ~/.aws/credentials).
 * 키를 설정 파일에 적으면 레포에 그대로 커밋된다.
 */
@ConfigurationProperties(prefix = "moasem.s3")
data class S3Properties(
    val bucket: String,
    val region: String,
)
