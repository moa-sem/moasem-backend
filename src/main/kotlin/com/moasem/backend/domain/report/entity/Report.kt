package com.moasem.backend.domain.report.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 행사 결산 보고서.
 *
 * 행사 하나당 한 건만 존재하며(`event_id` unique), 생성 시점의 결산 수치를
 * [snapshot]에 불변으로 보관한다.
 *
 * 보고서 생성 상태([status])와 AI 분석 상태([aiStatus])는 서로 독립적이다.
 * AI 분석이 실패해도 기본 PDF·CSV는 제공해야 하기 때문이다.
 */
@Entity
@Table(
    name = "reports",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_reports_event_id", columnNames = ["event_id"]),
    ],
)
class Report protected constructor(
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ReportStatus = ReportStatus.PENDING
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 20)
    var aiStatus: AiAnalysisStatus = AiAnalysisStatus.PENDING
        protected set

    /** 확정된 결산 결과. 한 번 채워지면 다시 바뀌지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    var snapshot: ReportSnapshot? = null
        protected set

    /** 목록 조회에서 스냅샷 전체를 역직렬화하지 않으려고 둔 비정규화 필드. 스냅샷 확정 시 함께 채운다. */
    @Column(name = "total_budget", nullable = false)
    var totalBudget: Long = 0L
        protected set

    @Column(name = "total_spent", nullable = false)
    var totalSpent: Long = 0L
        protected set

    @Column(name = "remaining_balance", nullable = false)
    var remainingBalance: Long = 0L
        protected set

    /** AI가 작성한 분석 텍스트. 금액은 담지 않는다. 모든 수치는 [snapshot]에서만 읽는다. */
    @Column(name = "ai_summary", columnDefinition = "text")
    var aiSummary: String? = null
        protected set

    @Column(name = "pdf_file_key", length = 512)
    var pdfFileKey: String? = null
        protected set

    @Column(name = "csv_file_key", length = 512)
    var csvFileKey: String? = null
        protected set

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null
        protected set

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0
        protected set

    @Column(name = "generated_at")
    var generatedAt: LocalDateTime? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null

    val isDownloadable: Boolean
        get() = status == ReportStatus.COMPLETED && pdfFileKey != null && csvFileKey != null

    /** 생성 작업을 시작한다. */
    fun startGenerating() {
        check(status == ReportStatus.PENDING || status == ReportStatus.FAILED) {
            "PENDING 또는 FAILED 상태에서만 생성을 시작할 수 있습니다. 현재 상태: $status"
        }
        status = ReportStatus.GENERATING
        failureReason = null
    }

    /**
     * 결산 결과를 확정한다.
     *
     * 재다운로드 시 동일한 결과를 보장해야 하므로 한 번만 호출할 수 있다.
     * 재시도 시에도 기존 스냅샷을 그대로 재사용한다.
     */
    fun applySnapshot(snapshot: ReportSnapshot) {
        check(this.snapshot == null) { "결산 스냅샷은 한 번 확정되면 변경할 수 없습니다." }

        this.snapshot = snapshot
        this.totalBudget = snapshot.budget.totalBudget
        this.totalSpent = snapshot.budget.totalSpent
        this.remainingBalance = snapshot.budget.remainingBalance
    }

    /** AI 분석 결과를 저장한다. */
    fun completeAiAnalysis(summary: String) {
        aiStatus = AiAnalysisStatus.SUCCEEDED
        aiSummary = summary
    }

    /**
     * AI 분석 실패를 기록한다.
     *
     * AI 실패는 보고서 생성 실패가 아니므로 [status]를 건드리지 않는다.
     * 기본 PDF·CSV는 그대로 생성되어야 한다.
     */
    fun failAiAnalysis() {
        aiStatus = AiAnalysisStatus.FAILED
        aiSummary = null
    }

    /** 파일 생성까지 끝나 보고서를 완성한다. */
    fun complete(pdfFileKey: String, csvFileKey: String) {
        this.pdfFileKey = pdfFileKey
        this.csvFileKey = csvFileKey
        this.status = ReportStatus.COMPLETED
        this.failureReason = null
        this.generatedAt = LocalDateTime.now()
    }

    /** 보고서 생성 자체가 실패했음을 기록한다. 재시도 대상이 된다. */
    fun fail(reason: String) {
        status = ReportStatus.FAILED
        failureReason = reason.take(FAILURE_REASON_MAX_LENGTH)
    }

    /** 재시도 횟수를 증가시킨다. */
    fun increaseRetryCount() {
        retryCount++
    }

    companion object {
        private const val FAILURE_REASON_MAX_LENGTH = 500

        /** 행사 마감 직후 호출한다. 아직 아무것도 계산되지 않은 빈 보고서를 만든다. */
        fun create(eventId: Long): Report = Report(eventId)
    }
}
