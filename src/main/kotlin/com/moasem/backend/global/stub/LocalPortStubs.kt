package com.moasem.backend.global.stub

import com.moasem.backend.domain.event.service.port.ApprovedSpendingTotalProvider
import com.moasem.backend.domain.event.service.port.GroupAccessProvider
import com.moasem.backend.domain.event.service.port.PendingSpendingCountProvider
import com.moasem.backend.domain.event.service.port.SpendingHistoryProvider
import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import com.moasem.backend.domain.report.service.port.ReportAiClient
import com.moasem.backend.domain.spending.service.port.GroupAccessProvider as SpendingGroupAccessProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 아직 구현체가 없는 port를 로컬에서만 임시로 채운다.
 *
 * spending의 모임 권한 조회와 AI 클라이언트에 아직 어댑터가 없고, 그 결과 애플리케이션이
 * 빈 주입 단계에서 기동조차 되지 않는다. 컨트롤러를 검증하거나 Swagger를 확인할 방법이
 * 없어 임시로 둔다.
 *
 * 안전장치 두 가지를 걸어 둔다.
 * - `@Profile("!prod")` — 운영 프로파일에서는 등록되지 않는다. 로컬과 테스트에서만 뜬다.
 * - `@ConditionalOnMissingBean` — 실제 어댑터가 생기면 그쪽이 우선한다. 담당자가 구현을
 *   추가할 때 이 파일을 건드리지 않아도 되고, 충돌도 나지 않는다.
 *
 * 실제 어댑터가 모두 채워지면 이 파일은 삭제한다.
 */
@Profile("!prod")
@Configuration
class LocalPortStubs {

    @Bean
    @ConditionalOnMissingBean(GroupAccessProvider::class)
    fun stubGroupAccessProvider() = object : GroupAccessProvider {
        override fun existsGroup(groupId: Long) = true
        override fun isMember(groupId: Long, userId: Long) = true
        override fun isOwner(groupId: Long, userId: Long) = true
    }

    /**
     * spending 도메인도 같은 이유로 자기 port를 따로 두고 있다.
     * group 도메인이 생기면 어댑터 하나가 두 port를 함께 구현하게 된다.
     */
    @Bean
    @ConditionalOnMissingBean(SpendingGroupAccessProvider::class)
    fun stubSpendingGroupAccessProvider() = object : SpendingGroupAccessProvider {
        override fun isMember(groupId: Long, userId: Long) = true
        override fun isOwner(groupId: Long, userId: Long) = true
    }

    @Bean
    @ConditionalOnMissingBean(PendingSpendingCountProvider::class)
    fun stubPendingSpendingCountProvider() = object : PendingSpendingCountProvider {
        override fun getPendingSpendingCount(eventId: Long) = 0L
    }

    @Bean
    @ConditionalOnMissingBean(ApprovedSpendingTotalProvider::class)
    fun stubApprovedSpendingTotalProvider() = object : ApprovedSpendingTotalProvider {
        override fun getApprovedSpendingTotal(eventId: Long) = 0L
    }

    @Bean
    @ConditionalOnMissingBean(SpendingHistoryProvider::class)
    fun stubSpendingHistoryProvider() = object : SpendingHistoryProvider {
        override fun hasAnySpending(eventId: Long) = false
    }

    @Bean
    @ConditionalOnMissingBean(ReportAiClient::class)
    fun stubReportAiClient() = object : ReportAiClient {
        override fun analyze(input: AiAnalysisInput): String =
            "[임시] AI 분석이 아직 연결되지 않았습니다."
    }
}
