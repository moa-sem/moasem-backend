package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.service.ReportGenerationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReportGenerationAdapterTest {

    private val reportGenerationService = mockk<ReportGenerationService>()
    private val adapter = ReportGenerationAdapter(reportGenerationService)

    @Test
    @DisplayName("마감 요청을 보고서 생성으로 전달한다")
    fun delegatesToGenerationService() {
        every { reportGenerationService.generate(1L) } returns mockk<Report>()

        adapter.requestReportGeneration(1L)

        verify(exactly = 1) { reportGenerationService.generate(1L) }
    }

    @Test
    @DisplayName("생성 실패는 그대로 전파한다")
    fun propagatesFailure() {
        // 마감 트랜잭션과의 분리는 EventCloseService가 afterCommit + try-catch로 처리한다.
        // 어댑터가 예외를 삼키면 그쪽에서 실패를 알 수 없다.
        every { reportGenerationService.generate(1L) } throws IllegalStateException("생성 실패")

        assertThatThrownBy { adapter.requestReportGeneration(1L) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
