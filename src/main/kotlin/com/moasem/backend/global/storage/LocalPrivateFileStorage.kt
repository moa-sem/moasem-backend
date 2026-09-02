package com.moasem.backend.global.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * 로컬 개발용 [PrivateFileStorage]. S3 대신 디스크에 저장한다.
 *
 * 팀원 대부분이 IAM 키를 갖고 있지 않다. 이게 없으면 증빙 업로드부터 조회까지의 흐름을
 * 로컬에서 한 번도 돌려볼 수 없다.
 *
 * S3 어댑터와 달리 URL에 서명이 없다. 만료도, 형식·크기 제한도 적용되지 않는다.
 * 링크를 아는 누구나 읽고 쓸 수 있으므로 `@Profile("local")`로 로컬에만 묶어 둔다.
 * 실제 업로드·조회를 처리하는 엔드포인트는
 * [com.moasem.backend.global.dev.DevPrivateFileController]에 있다.
 */
@Profile("local")
@Component
class LocalPrivateFileStorage(
    @Value("\${moasem.local-storage.private-dir:build/local-private-files}") baseDir: String,
    @Value("\${moasem.local-storage.base-url:http://localhost:8080}") private val baseUrl: String,
) : PrivateFileStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    private val root: Path = Path.of(baseDir).toAbsolutePath().normalize()

    /** 만료·형식·크기는 받기만 하고 쓰지 않는다. 서명이 없어 강제할 수단이 없다. */
    override fun issueUploadUrl(key: String, contentType: String, contentLength: Long, expiry: Duration): String =
        "$baseUrl$PATH_PREFIX$key"

    override fun issueDownloadUrl(key: String, expiry: Duration): String = "$baseUrl$PATH_PREFIX$key"

    fun write(key: String, content: ByteArray) {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.write(target, content)

        log.info("로컬 저장소에 비공개 파일 저장. key={} path={} size={}B", key, target, content.size)
    }

    /** 저장된 파일을 읽는다. 없으면 null. */
    fun read(key: String): ByteArray? = resolve(key).takeIf { Files.isRegularFile(it) }?.let(Files::readAllBytes)

    /**
     * key를 실제 경로로 바꾼다.
     *
     * 서빙 시 key는 URL에서 그대로 넘어온다. `../`가 섞이면 저장소 밖 파일을 읽거나
     * 덮어쓸 수 있어, 정규화 후 루트 안인지 확인한다.
     */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root)) { "저장소 밖 경로에 접근할 수 없습니다. key=$key" }
        return resolved
    }

    companion object {
        const val PATH_PREFIX = "/api/v1/dev/private-files/"
    }
}
