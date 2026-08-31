package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.report.service.port.ReportFileStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * 로컬 개발용 [ReportFileStorage]. S3 대신 디스크에 저장한다.
 *
 * AWS 자격증명 없이 보고서 생성부터 다운로드까지 확인할 수 있게 하려고 둔다.
 * 팀원 대부분이 IAM 키를 갖고 있지 않아, 이게 없으면 생성된 PDF를 눈으로 볼 방법이 없다.
 *
 * S3 어댑터와 달리 URL에 서명이 없다. 만료도 적용되지 않는다.
 * 링크를 아는 누구나 받을 수 있으므로 `@Profile("local")`로 로컬에만 묶어 둔다.
 * 운영에서는 [S3ReportFileStorage]가 쓰인다.
 */
@Profile("local")
@Component
class LocalReportFileStorage(
    @Value("\${moasem.local-storage.dir:build/local-report-files}") baseDir: String,
    @Value("\${moasem.local-storage.base-url:http://localhost:8080}") private val baseUrl: String,
) : ReportFileStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    private val root: Path = Path.of(baseDir).toAbsolutePath().normalize()

    override fun upload(key: String, content: ByteArray, contentType: String): String {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.write(target, content)

        log.info("로컬 저장소에 보고서 파일 저장. key={} path={} size={}B", key, target, content.size)
        return key
    }

    /**
     * 만료 시간은 받기만 하고 쓰지 않는다.
     *
     * 서명을 붙이려면 검증하는 쪽도 있어야 하는데, 로컬에서 그만한 장치를 둘 이유가 없다.
     * 대신 이 구현이 운영에 올라가지 않도록 프로파일로 막는다.
     */
    override fun generateDownloadUrl(key: String, expiry: Duration): String =
        "$baseUrl$DOWNLOAD_PATH_PREFIX$key"

    /** 저장된 파일을 읽는다. 없으면 null. */
    fun read(key: String): ByteArray? = resolve(key).takeIf { Files.isRegularFile(it) }?.let(Files::readAllBytes)

    /**
     * key를 실제 경로로 바꾼다.
     *
     * key는 저장 시엔 우리 코드가 만들지만 서빙 시엔 URL에서 그대로 넘어온다.
     * `../`가 섞이면 저장소 밖 파일을 읽어갈 수 있어, 정규화 후 루트 안인지 확인한다.
     */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root)) { "저장소 밖 경로에 접근할 수 없습니다. key=$key" }
        return resolved
    }

    companion object {
        const val DOWNLOAD_PATH_PREFIX = "/api/v1/dev/files/"
    }
}
