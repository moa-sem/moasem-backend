package com.moasem.backend.domain.event.service.port

/**
 * 마감된 행사의 결산 보고서 생성을 요청하는 경계다.
 * 실제 report 어댑터는 후속 연동에서 제공한다.
 */
interface ReportGenerationRequester {

    fun requestReportGeneration(eventId: Long)
}
