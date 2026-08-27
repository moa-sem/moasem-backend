package com.moasem.backend.domain.report.service

import com.moasem.backend.domain.report.entity.ReportSnapshot
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Component
import java.io.StringWriter
import java.time.format.DateTimeFormatter

/**
 * 확정된 스냅샷을 CSV로 변환한다.
 *
 * 엑셀에서 다시 가공하는 것이 목적이므로 꾸밈 없이 데이터만 담는다.
 * AI 분석 코멘트는 포함하지 않는다 — 재계산 대상이 아닌 해석 문장이고,
 * 표 형태로 다룰 값도 아니기 때문이다.
 */
@Component
class ReportCsvGenerator {

    fun generate(snapshot: ReportSnapshot): ByteArray {
        val body = StringWriter().use { writer ->
            CSVPrinter(writer, CSVFormat.DEFAULT).use { printer ->
                printSummary(printer, snapshot)
                printer.println()
                printSpendings(printer, snapshot)
            }
            writer.toString()
        }

        // 엑셀은 BOM이 없으면 CSV를 시스템 기본 인코딩으로 읽어 한글이 깨진다.
        return UTF8_BOM + body.toByteArray(Charsets.UTF_8)
    }

    private fun printSummary(printer: CSVPrinter, snapshot: ReportSnapshot) {
        printer.printRecord("행사명", snapshot.event.title)
        printer.printRecord("모임", snapshot.event.groupName)
        printer.printRecord("기간", "${snapshot.event.startAt.format(DATE)} ~ ${snapshot.event.endAt.format(DATE)}")
        snapshot.event.participantCount?.let { printer.printRecord("참여 인원", it) }
        printer.println()
        printer.printRecord("총 예산", snapshot.budget.totalBudget)
        printer.printRecord("총 지출", snapshot.budget.totalSpent)
        printer.printRecord("남은 금액", snapshot.budget.remainingBalance)
    }

    private fun printSpendings(printer: CSVPrinter, snapshot: ReportSnapshot) {
        // 태그 코드(MEAL) 대신 한글 라벨(식비)을 쓴다. 사람이 엑셀에서 읽을 파일이기 때문이다.
        // 라벨은 tagTotals가 이미 갖고 있으므로 스냅샷 구조를 바꾸지 않고 가져다 쓴다.
        val labels = snapshot.tagTotals.associate { it.tag to it.label }

        printer.printRecord(*SPENDING_HEADERS)
        snapshot.spendings.forEach {
            printer.printRecord(
                it.spentAt.format(DATE_TIME),
                it.description,
                labels[it.tag] ?: it.tag,
                it.amount,
                it.payerName,
            )
        }
    }

    companion object {
        const val CONTENT_TYPE = "text/csv"

        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val SPENDING_HEADERS = arrayOf("일시", "항목", "태그", "금액", "결제자")
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
