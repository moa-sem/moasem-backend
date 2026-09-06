package com.moasem.backend.global.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 비동기 실행 설정.
 *
 * 스레드풀을 직접 정의하는 이유는, 지정하지 않으면 스프링이 요청마다 새 스레드를 만드는
 * 실행기를 쓰기 때문이다. 마감이 몰리면 스레드 수가 제한 없이 늘어난다.
 */
@EnableAsync
@Configuration
class AsyncConfig : AsyncConfigurer {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 보고서 생성 전용 풀.
     *
     * 한 건에 수 초가 걸리고 대부분이 AI 응답과 S3 업로드를 기다리는 시간이다.
     * CPU를 쓰는 구간이 짧아 코어 수에 맞출 이유가 없다.
     *
     * 큐가 차면 [ThreadPoolExecutor.CallerRunsPolicy]로 호출한 스레드가 직접 처리한다.
     * 버리지 않는 이유는, 여기서 버리면 보고서 행조차 만들어지지 않아 사용자가
     * 재시도할 대상도 없이 "보고서 없음"만 보게 되기 때문이다. 밀릴 때는 느려질지언정
     * 요청이 사라지지는 않는 편이 낫다.
     */
    @Bean(REPORT_EXECUTOR)
    fun reportTaskExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 50
        setThreadNamePrefix("report-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        // 종료 시 진행 중인 생성을 끝내고 내려간다. 중간에 끊기면 GENERATING 상태로 남는다.
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
        initialize()
    }

    /**
     * 반환값이 없는 비동기 메서드에서 던져진 예외를 받는다.
     *
     * 이걸 두지 않으면 예외가 아무 데도 기록되지 않고 사라진다. 호출한 쪽은 이미 응답을
     * 보낸 뒤라 알 방법이 없다.
     */
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler =
        AsyncUncaughtExceptionHandler { throwable: Throwable, method: Method, params: Array<out Any?> ->
            log.error("비동기 작업 실패. method={} params={}", method.name, params.joinToString(), throwable)
        }

    companion object {
        const val REPORT_EXECUTOR = "reportTaskExecutor"

        private const val AWAIT_TERMINATION_SECONDS = 30
    }
}
