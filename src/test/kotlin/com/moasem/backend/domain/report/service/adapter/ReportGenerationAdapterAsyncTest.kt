package com.moasem.backend.domain.report.service.adapter

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.service.ReportGenerationService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 보고서 생성이 요청 스레드를 붙잡지 않는지 확인한다.
 *
 * @Async는 프록시를 통해서만 동작한다. 어노테이션이 붙어 있어도 설정이 빠지거나 자기
 * 호출이 되면 조용히 동기로 돌고, 단위 테스트로는 그 차이가 드러나지 않는다.
 * 그래서 컨텍스트를 띄워 실제 실행 스레드를 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportGenerationAdapterAsyncTest {

    @Autowired
    private lateinit var adapter: ReportGenerationAdapter

    @MockkBean
    private lateinit var reportGenerationService: ReportGenerationService

    @Test
    @DisplayName("생성은 호출 스레드가 아닌 전용 풀에서 실행된다")
    fun runsOnReportExecutor() {
        val executionThread = AtomicReference<String>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        every { reportGenerationService.generate(EVENT_ID) } answers {
            executionThread.set(Thread.currentThread().name)
            started.countDown()
            // 호출한 쪽이 정말 기다리지 않는지 보려면 작업을 붙잡아 둬야 한다.
            release.await(5, TimeUnit.SECONDS)
            Report.create(EVENT_ID)
        }

        adapter.requestReportGeneration(EVENT_ID)

        // 작업이 아직 끝나지 않았는데도 호출은 이미 반환됐다.
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(executionThread.get())
            .isNotEqualTo(Thread.currentThread().name)
            .startsWith("report-")

        release.countDown()
    }

    @Test
    @DisplayName("생성이 실패해도 호출한 쪽으로 예외가 전파되지 않는다")
    fun swallowsFailureFromCaller() {
        val called = CountDownLatch(1)
        every { reportGenerationService.generate(EVENT_ID) } answers {
            called.countDown()
            // 마감 트랜잭션은 이미 커밋됐다. 여기서 던진 예외가 마감을 되돌리면 안 된다.
            throw IllegalStateException("보고서 생성 실패")
        }

        adapter.requestReportGeneration(EVENT_ID)

        await().atMost(Duration.ofSeconds(5)).until { called.count == 0L }
    }

    companion object {
        private const val EVENT_ID = 1L
    }
}
