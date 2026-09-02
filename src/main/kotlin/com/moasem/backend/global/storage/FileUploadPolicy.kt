package com.moasem.backend.global.storage

import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode

/**
 * 저장소에 올릴 수 있는 파일의 제약.
 *
 * 업로드 URL을 발급하는 시점과 업로드 결과를 엔티티에 반영하는 시점 양쪽에서 같은 규칙을
 * 써야 하므로 한 곳에 모은다. 용도마다 제약이 다르므로 정책 인스턴스를 상수로 둔다.
 */
data class FileUploadPolicy(
    val allowedMimeTypes: Set<String>,
    val maxSizeBytes: Long,
) {

    /**
     * 형식과 용량을 검증한다.
     *
     * [sizeBytes]가 null이면 크기를 알 수 없는 경우로 보고 용량 검증을 건너뛴다.
     * 업로드 URL 발급 시에는 크기가 URL에 묶이므로 항상 실제 값을 넘긴다.
     *
     * 클라이언트가 보낸 값으로 걸리는 규칙이라 [BusinessException]으로 던진다.
     * 허용 목록은 `@Valid`로 표현할 수 없어(정책마다 다르다) 여기가 유일한 검증 지점이다.
     */
    fun validate(mimeType: String, sizeBytes: Long?) {
        if (mimeType !in allowedMimeTypes) {
            throw BusinessException(
                ErrorCode.UNSUPPORTED_FILE_TYPE,
                "허용하지 않는 파일 형식입니다. mimeType=$mimeType (허용: ${allowedMimeTypes.joinToString()})",
            )
        }
        if (sizeBytes != null && sizeBytes !in 1..maxSizeBytes) {
            throw BusinessException(
                ErrorCode.FILE_SIZE_EXCEEDED,
                "파일 크기는 1바이트 이상 ${maxSizeBytes}바이트 이하여야 합니다. sizeBytes=$sizeBytes",
            )
        }
    }

    companion object {
        /** 지출 증빙 이미지. 휴대폰 사진 원본이 들어와도 통과하는 크기다. */
        val EVIDENCE_IMAGE = FileUploadPolicy(
            allowedMimeTypes = setOf("image/jpeg", "image/png"),
            maxSizeBytes = 10L * 1024 * 1024,
        )
    }
}
