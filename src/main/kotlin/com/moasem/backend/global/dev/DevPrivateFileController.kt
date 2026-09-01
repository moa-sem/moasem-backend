package com.moasem.backend.global.dev

import com.moasem.backend.global.storage.LocalPrivateFileStorage
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬에서 presigned URL 흐름을 대신하는 임시 컨트롤러.
 *
 * 운영에서는 클라이언트가 S3에 직접 PUT/GET 하고 서버는 URL만 발급한다. 로컬에는 S3가 없어
 * 발급된 URL이 가리킬 곳이 없으므로, 같은 경로를 앱이 받아 디스크에 읽고 쓴다.
 *
 * S3 자격증명이 팀에 배포되면 이 컨트롤러와 [LocalPrivateFileStorage]는 함께 지운다.
 */
@Tag(name = "Dev", description = "로컬 전용 임시 API")
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
class DevPrivateFileController(
    private val fileStorage: LocalPrivateFileStorage,
) {

    /**
     * 발급된 업로드 URL로 들어온 PUT을 받아 저장한다.
     *
     * key에 `/`가 들어 있어(`spendings/1/42/{uuid}.jpg`) `@PathVariable`로는 받을 수 없다.
     * 와일드카드로 받고 요청 경로에서 접두사를 잘라 낸다.
     */
    @PutMapping("/private-files/**")
    fun upload(request: HttpServletRequest, @RequestBody content: ByteArray): ResponseEntity<Unit> {
        fileStorage.write(keyOf(request), content)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/private-files/**")
    fun download(request: HttpServletRequest): ResponseEntity<ByteArray> {
        val key = keyOf(request)
        val content = fileStorage.read(key) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentTypeOf(key))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${key.substringAfterLast('/')}\"")
            .body(content)
    }

    private fun keyOf(request: HttpServletRequest): String =
        request.requestURI.substringAfter(LocalPrivateFileStorage.PATH_PREFIX)

    private fun contentTypeOf(key: String) = when (key.substringAfterLast('.')) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
}
