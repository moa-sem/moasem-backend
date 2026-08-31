package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.event.service.port.ReportGenerationRequester
import com.moasem.backend.domain.report.service.ReportGenerationService
import org.springframework.stereotype.Component

/**
 * event 도메인의 보고서 생성 요청을 report 도메인으로 잇는다.
 *
 * event는 [ReportGenerationRequester] 인터페이스만 알고 report의 내부 구조는 모른다.
 * report 쪽 클래스나 시그니처가 바뀌어도 이 어댑터만 고치면 된다.
 *
 * 호출 시점은 [com.moasem.backend.domain.event.service.EventCloseService]가 트랜잭션
 * 커밋 이후로 잡아 두었다. 보고서 생성이 실패해도 행사 마감은 되돌아가지 않는다.
 */
@Component
class ReportGenerationAdapter(
    private val reportGenerationService: ReportGenerationService,
) : ReportGenerationRequester {

    override fun requestReportGeneration(eventId: Long) {
        reportGenerationService.generate(eventId)
    }
}
