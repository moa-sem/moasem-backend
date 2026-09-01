package com.moasem.backend.global.storage

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FileUploadPolicyTest {

    private val policy = FileUploadPolicy.EVIDENCE_IMAGE

    @ParameterizedTest
    @ValueSource(strings = ["image/jpeg", "image/png"])
    fun `증빙 이미지는 JPEG와 PNG를 허용한다`(mimeType: String) {
        assertThatCode { policy.validate(mimeType, 1_024L) }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/pdf", "image/gif", "image/jpg", "IMAGE/JPEG", "text/plain"])
    fun `그 외 형식은 거부한다`(mimeType: String) {
        assertThatThrownBy { policy.validate(mimeType, 1_024L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("허용하지 않는 파일 형식")
    }

    @Test
    fun `상한과 같은 크기는 허용하고 한 바이트라도 넘으면 거부한다`() {
        assertThatCode { policy.validate("image/jpeg", policy.maxSizeBytes) }.doesNotThrowAnyException()

        assertThatThrownBy { policy.validate("image/jpeg", policy.maxSizeBytes + 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("파일 크기")
    }

    @Test
    fun `빈 파일은 거부한다`() {
        assertThatThrownBy { policy.validate("image/jpeg", 0L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("파일 크기")
    }

    @Test
    fun `크기를 알 수 없으면 용량 검증을 건너뛴다`() {
        assertThatCode { policy.validate("image/jpeg", null) }.doesNotThrowAnyException()
    }
}
