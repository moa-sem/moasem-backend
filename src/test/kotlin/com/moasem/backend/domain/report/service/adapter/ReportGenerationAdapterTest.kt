package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.service.ReportGenerationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 어댑터가 생성 서비스로 제대로 위임하는지 본다.
 *
 * 여기서는 스프링 컨텍스트가 없어 @Async가 적용되지 않고 그대로 동기 실행된다.
 * 비동기로 도는지는 [ReportGenerationAdapterAsyncTest]가 확인한다.
 */
class ReportGenerationAdapterTest {

    private val reportGenerationService = mockk<ReportGenerationService>()
    private val adapter = ReportGenerationAdapter(reportGenerationService)

    @Test
    @DisplayName("마감 요청을 보고서 생성으로 전달한다")
    fun delegatesToGenerationService() {
        // 어댑터가 종료 로그에 상태를 남기므로 relaxed 모의 객체를 쓴다.
        every { reportGenerationService.generate(1L) } returns mockk<Report>(relaxed = true)

        adapter.requestReportGeneration(1L)

        verify(exactly = 1) { reportGenerationService.generate(1L) }
    }

    @Test
    @DisplayName("생성 실패는 그대로 전파한다")
    fun propagatesFailure() {
        // 어댑터가 예외를 삼키면 안 된다. 실제 실행에서는 @Async 경계를 넘어
        // AsyncConfig의 예외 처리기가 받아 기록한다.
        every { reportGenerationService.generate(1L) } throws IllegalStateException("생성 실패")

        assertThatThrownBy { adapter.requestReportGeneration(1L) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
