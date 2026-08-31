package com.moasem.backend.domain.report.dto

import com.moasem.backend.domain.report.entity.AiAnalysisStatus
import com.moasem.backend.domain.report.entity.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 보고서 생성 상태.
 *
 * 행사 마감 직후 생성이 끝날 때까지 프론트가 폴링한다.
 */
@Schema(description = "보고서 생성 상태")
data class ReportStatusResponse(
    @field:Schema(description = "행사 ID")
    val eventId: Long,

    @field:Schema(description = "보고서 생성 상태")
    val status: ReportStatus,

    @field:Schema(description = "AI 분석 상태")
    val aiStatus: AiAnalysisStatus,

    @field:Schema(description = "다운로드 가능 여부")
    val downloadable: Boolean,

    @field:Schema(description = "재시도 가능 여부")
    val retryable: Boolean,

    @field:Schema(description = "생성 실패 사유. 실패한 경우에만 존재한다")
    val failureReason: String?,

    @field:Schema(description = "생성 완료 시각")
    val generatedAt: LocalDateTime?,
)

/**
 * 결산 보고서 내용.
 *
 * 모든 수치는 확정된 스냅샷에서 나온다. 원본 지출 데이터를 다시 조회하지 않으므로
 * 몇 번을 조회해도 같은 값이 나온다.
 */
@Schema(description = "결산 보고서")
data class ReportDetailResponse(
    @field:Schema(description = "행사 ID")
    val eventId: Long,

    @field:Schema(description = "보고서 생성 상태")
    val status: ReportStatus,

    @field:Schema(description = "AI 분석 상태. FAILED면 aiSummary가 없다")
    val aiStatus: AiAnalysisStatus,

    @field:Schema(description = "AI 결산 분석. 분석에 실패했으면 null")
    val aiSummary: String?,

    val event: EventSummaryResponse,
    val budget: BudgetSummaryResponse,

    @field:Schema(description = "태그별 지출 집계. 금액 내림차순")
    val tagTotals: List<TagTotalResponse>,

    @field:Schema(description = "지출 내역. 지출 일시 오름차순")
    val spendings: List<SpendingLineResponse>,

    @field:Schema(description = "생성 완료 시각")
    val generatedAt: LocalDateTime?,
)

@Schema(description = "행사 정보")
data class EventSummaryResponse(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val groupName: String,

    @field:Schema(description = "참여 인원. 마감 시 입력된 값")
    val participantCount: Int?,
)

@Schema(description = "결산 요약")
data class BudgetSummaryResponse(
    val initialBudget: Long,

    @field:Schema(description = "최초 예산 + 추가 예산 합계")
    val totalBudget: Long,

    @field:Schema(description = "승인된 지출 합계")
    val totalSpent: Long,

    @field:Schema(description = "총예산 - 총지출. 예산을 초과하면 음수")
    val remainingBalance: Long,

    val additions: List<BudgetAdditionResponse>,
)

@Schema(description = "추가 예산 내역")
data class BudgetAdditionResponse(
    val amount: Long,
    val reason: String,
    val addedBy: String?,
    val addedAt: LocalDateTime,
)

@Schema(description = "태그별 지출 집계")
data class TagTotalResponse(
    @field:Schema(description = "태그 코드", example = "MEAL")
    val tag: String,

    @field:Schema(description = "표시용 한글 라벨", example = "식비")
    val label: String,

    val amount: Long,
    val count: Int,
)

@Schema(description = "지출 내역 한 건")
data class SpendingLineResponse(
    val spendingId: Long,
    val description: String,
    val amount: Long,
    val tag: String,

    @field:Schema(description = "표시용 한글 라벨", example = "식비")
    val label: String,

    val payerName: String,
    val spentAt: LocalDateTime,
    val receiptUrl: String?,
)
