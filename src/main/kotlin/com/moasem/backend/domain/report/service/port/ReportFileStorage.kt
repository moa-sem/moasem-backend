package com.moasem.backend.domain.report.service.port

import java.time.Duration

/**
 * 생성된 보고서 파일의 저장과 다운로드 링크 발급을 담당한다.
 *
 * 파일 내용을 만드는 책임(PDF·CSV 렌더링)도, HTTP로 노출하는 책임(Controller)도 갖지 않는다.
 * "어디에 어떻게 두고 어떻게 꺼내주는가"만 다룬다.
 */
interface ReportFileStorage {

    /**
     * 파일을 저장하고 저장 위치(key)를 반환한다.
     *
     * 반환된 key를 `Report.pdfFileKey` / `Report.csvFileKey`에 보관했다가
     * 다운로드 시 [generateDownloadUrl]에 넘긴다.
     */
    fun upload(key: String, content: ByteArray, contentType: String): String

    /**
     * 지정한 기간 동안만 유효한 다운로드 URL을 발급한다.
     *
     * 발급된 URL은 그 자체가 통행증이므로, 호출 전에 반드시 행사 마감 여부와
     * 모임 구성원 여부를 검증해야 한다. 유효 기간은 짧게 유지한다.
     */
    fun generateDownloadUrl(key: String, expiry: Duration): String
}
