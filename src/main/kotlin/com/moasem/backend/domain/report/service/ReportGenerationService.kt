package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.Report
import com.moasem.backend.domain.report.entity.ReportSnapshot
import com.moasem.backend.domain.report.repository.ReportRepository
import com.moasem.backend.domain.report.service.port.AiAnalysisInput
import com.moasem.backend.domain.report.service.port.EventSnapshotProvider
import com.moasem.backend.domain.report.service.port.ReportAiClient
import com.moasem.backend.domain.report.service.port.ReportAiException
import com.moasem.backend.domain.report.service.port.ReportFileStorage
import com.moasem.backend.domain.report.service.port.TagTotalData
import com.moasem.backend.global.error.BusinessException
import com.moasem.backend.global.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 행사 마감 이후 보고서 생성 전체 흐름을 조립한다.
 *
 * 각 부품(계산기·생성기·저장소·AI)은 자기 일만 알고, 순서와 실패 처리는 여기서만 결정한다.
 * 실패 정책이 여러 곳에 흩어지면 한쪽만 고쳐졌을 때 동작이 어긋나기 때문이다.
 */
@Service
class ReportGenerationService(
    private val reportRepository: ReportRepository,
    private val eventSnapshotProvider: EventSnapshotProvider,
    private val snapshotCalculator: ReportSnapshotCalculator,
    private val pdfGenerator: ReportPdfGenerator,
    private val csvGenerator: ReportCsvGenerator,
    private val fileStorage: ReportFileStorage,
    private val aiClient: ReportAiClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 행사 마감 직후 호출한다. 보고서를 만들고 파일까지 저장한다.
     *
     * @throws BusinessException 이미 보고서가 있거나 마감되지 않은 행사인 경우
     */
    @Transactional
    fun generate(eventId: Long): Report {
        if (reportRepository.existsByEventId(eventId)) {
            throw BusinessException(ErrorCode.REPORT_ALREADY_EXISTS)
        }

        // 스냅샷을 먼저 확정한 뒤에 보고서 행을 만든다. 마감되지 않은 행사처럼 사전 조건을
        // 어긴 경우에 FAILED 행이 남으면, 나중에 행사가 실제로 마감돼도 event_id unique 제약
        // 때문에 새 보고서를 만들 수 없게 된다.
        val snapshot = snapshotCalculator.calculate(eventSnapshotProvider.fetch(eventId))

        val report = reportRepository.save(Report.create(eventId))
        report.startGenerating()
        report.applySnapshot(snapshot)

        return runGeneration(report, snapshot)
    }

    /**
     * 생성에 실패한 보고서를 다시 만든다.
     *
     * 확정된 스냅샷은 다시 계산하지 않고 그대로 재사용한다. 재계산하면 그사이 원본 데이터가
     * 바뀌었을 때 금액이 달라져 "재다운로드 시 동일한 결과" 보장이 깨진다.
     */
    @Transactional
    fun retry(eventId: Long): Report {
        val report = reportRepository.findByEventId(eventId)
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)

        if (!report.status.isRetryable) {
            throw BusinessException(
                ErrorCode.REPORT_NOT_RETRYABLE,
                "재시도할 수 없는 상태입니다. 현재 상태: ${report.status}",
            )
        }

        // 보고서 행은 스냅샷 확정 이후에만 만들어지므로 여기서 snapshot은 항상 존재한다.
        val snapshot = checkNotNull(report.snapshot) {
            "스냅샷이 없는 보고서입니다. eventId=${report.eventId}"
        }

        report.increaseRetryCount()
        report.startGenerating()

        return runGeneration(report, snapshot)
    }

    private fun runGeneration(report: Report, snapshot: ReportSnapshot): Report = try {
        val aiSummary = analyze(report, snapshot)
        storeFiles(report, snapshot, aiSummary)
        report
    } catch (e: Exception) {
        // 파일 생성·저장 실패는 보고서 실패다. AI 실패는 여기까지 오지 않는다.
        log.error("보고서 생성 실패. eventId={}", report.eventId, e)
        report.fail(e.message ?: e.javaClass.simpleName)
        report
    }

    /**
     * AI 분석을 시도한다.
     *
     * 실패해도 예외를 밖으로 던지지 않는다. AI는 부가 기능이고, 실패가 보고서 생성 전체를
     * 멈추면 안 되기 때문이다. 실패 사실은 aiStatus에만 남기고 파일 생성은 그대로 진행한다.
     */
    private fun analyze(report: Report, snapshot: ReportSnapshot): String? = try {
        val summary = aiClient.analyze(toAiInput(snapshot))
        report.completeAiAnalysis(summary)
        summary
    } catch (e: ReportAiException) {
        log.warn("AI 결산 분석 실패. 보고서 생성은 계속 진행한다. eventId={}", report.eventId, e)
        report.failAiAnalysis()
        null
    }

    private fun storeFiles(report: Report, snapshot: ReportSnapshot, aiSummary: String?) {
        val pdfKey = fileStorage.upload(
            key = fileKey(report.eventId, "pdf"),
            content = pdfGenerator.generate(snapshot, aiSummary),
            contentType = ReportPdfGenerator.CONTENT_TYPE,
        )
        val csvKey = fileStorage.upload(
            key = fileKey(report.eventId, "csv"),
            content = csvGenerator.generate(snapshot),
            contentType = ReportCsvGenerator.CONTENT_TYPE,
        )
        report.complete(pdfFileKey = pdfKey, csvFileKey = csvKey)
    }

    /** AI에는 집계값만 넘긴다. 개별 지출을 주지 않아 금액을 재계산할 여지를 없앤다. */
    private fun toAiInput(snapshot: ReportSnapshot) = AiAnalysisInput(
        eventTitle = snapshot.event.title,
        totalBudget = snapshot.budget.totalBudget,
        totalSpent = snapshot.budget.totalSpent,
        remainingBalance = snapshot.budget.remainingBalance,
        tagTotals = snapshot.tagTotals.map {
            TagTotalData(tag = it.tag, label = it.label, amount = it.amount, count = it.count)
        },
    )

    private fun fileKey(eventId: Long, extension: String) = "reports/$eventId/report.$extension"
}
