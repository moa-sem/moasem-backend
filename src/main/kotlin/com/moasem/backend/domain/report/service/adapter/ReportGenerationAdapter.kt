package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.event.service.port.ReportGenerationRequester
import com.moasem.backend.domain.report.service.ReportGenerationService
import com.moasem.backend.global.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * event 도메인의 보고서 생성 요청을 report 도메인으로 잇는다.
 *
 * event는 [ReportGenerationRequester] 인터페이스만 알고 report의 내부 구조는 모른다.
 * report 쪽 클래스나 시그니처가 바뀌어도 이 어댑터만 고치면 된다.
 *
 * 호출 시점은 [com.moasem.backend.domain.event.service.EventCloseService]가 트랜잭션
 * 커밋 이후로 잡아 두었다. 보고서 생성이 실패해도 행사 마감은 되돌아가지 않는다.
 *
 * 생성은 별도 스레드에서 돈다. AI 호출과 파일 업로드에 수 초가 걸리는데, 그동안 마감
 * 응답을 붙잡아 두면 사용자가 버튼을 누른 채 기다리게 된다. 진행 상황은 상태 조회 API로
 * 확인하고, 실패하면 재시도 API로 다시 만든다.
 */
@Component
class ReportGenerationAdapter(
    private val reportGenerationService: ReportGenerationService,
) : ReportGenerationRequester {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 호출 즉시 반환하고 생성은 백그라운드에서 진행한다.
     *
     * 예외를 여기서 잡지 않는다. 던지면 [AsyncConfig]의 예외 처리기가 기록한다.
     * 삼키면 호출한 쪽은 이미 응답을 보낸 뒤라 실패를 알 방법이 없어진다.
     *
     * 생성 실패 자체는 [ReportGenerationService]가 보고서 상태를 FAILED로 남겨
     * 사용자가 재시도할 수 있게 한다. 여기까지 올라오는 건 보고서 행조차 만들지
     * 못한 경우다.
     */
    @Async(AsyncConfig.REPORT_EXECUTOR)
    override fun requestReportGeneration(eventId: Long) {
        log.info("보고서 생성 시작. eventId={}", eventId)
        val report = reportGenerationService.generate(eventId)
        log.info("보고서 생성 종료. eventId={} status={}", eventId, report.status)
    }
}
