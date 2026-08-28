package com.moasem.backend.domain.report.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.moasem.backend.domain.report.entity.ReportSnapshot
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 확정된 스냅샷을 PDF 보고서로 렌더링한다.
 *
 * 사람이 읽고 공유하는 문서이므로 금액에 천단위 구분자를 넣고 요약을 앞에 둔다.
 * AI 분석은 선택 사항이다. aiSummary가 null이면 해당 섹션만 빠지고 나머지는 그대로 만들어진다.
 */
@Component
class ReportPdfGenerator {

    fun generate(snapshot: ReportSnapshot, aiSummary: String? = null): ByteArray {
        val output = ByteArrayOutputStream()

        PdfDocument(PdfWriter(output)).use { pdf ->
            Document(pdf, PageSize.A4).use { document ->
                Renderer(document).render(snapshot, aiSummary)
            }
        }

        return output.toByteArray()
    }

    /**
     * 문서 하나를 렌더링한다.
     *
     * 폰트를 필드로 들고 있기 때문에 문서마다 새로 만들어야 한다. iText의 PdfFont는 처음
     * 사용된 PdfDocument에 귀속되어 다른 문서에서 재사용할 수 없다.
     */
    private class Renderer(private val document: Document) {

        private val regular: PdfFont = createFont(regularFontBytes)
        private val bold: PdfFont = createFont(boldFontBytes)

        fun render(snapshot: ReportSnapshot, aiSummary: String?) {
            document.setFont(regular)

            writeHeader(snapshot)
            writeBudgetSummary(snapshot)
            writeTagTotals(snapshot)
            aiSummary?.let { writeAiAnalysis(it) }
            writeSpendings(snapshot)
        }

        private fun writeHeader(snapshot: ReportSnapshot) {
            val event = snapshot.event

            document.add(
                Paragraph("${event.title} 결산 보고서")
                    .setFont(bold)
                    .setFontSize(20f)
                    .setMarginBottom(4f),
            )

            val subtitle = buildString {
                append(event.groupName)
                append("  ·  ")
                append(event.startAt.format(DATE))
                append(" ~ ")
                append(event.endAt.format(DATE))
                event.participantCount?.let {
                    append("  ·  참여 ")
                    append(it)
                    append("명")
                }
            }
            document.add(
                Paragraph(subtitle)
                    .setFontSize(10f)
                    .setFontColor(MUTED)
                    .setMarginBottom(20f),
            )
        }

        private fun writeBudgetSummary(snapshot: ReportSnapshot) {
            val budget = snapshot.budget
            val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
                .useAllAvailableWidth()
                .setMarginBottom(20f)

            table.addCell(summaryLabel("총 예산"))
            table.addCell(summaryLabel("총 지출"))
            table.addCell(summaryLabel("남은 금액"))

            table.addCell(summaryValue(budget.totalBudget))
            table.addCell(summaryValue(budget.totalSpent))
            // 예산을 초과하면 음수다. 눈에 띄게 붉은색으로 표시한다.
            table.addCell(
                summaryValue(
                    budget.remainingBalance,
                    if (budget.remainingBalance < 0) DANGER else PRIMARY,
                ),
            )

            document.add(table)
        }

        private fun writeTagTotals(snapshot: ReportSnapshot) {
            if (snapshot.tagTotals.isEmpty()) return

            document.add(sectionTitle("항목별 지출"))

            val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 1f, 2f)))
                .useAllAvailableWidth()
                .setMarginBottom(20f)

            table.addHeaderCell(headerCell("항목", TextAlignment.LEFT))
            table.addHeaderCell(headerCell("건수", TextAlignment.RIGHT))
            table.addHeaderCell(headerCell("금액", TextAlignment.RIGHT))

            snapshot.tagTotals.forEach {
                table.addCell(bodyCell(it.label, TextAlignment.LEFT))
                table.addCell(bodyCell("${it.count}건", TextAlignment.RIGHT))
                table.addCell(bodyCell(money(it.amount), TextAlignment.RIGHT))
            }

            document.add(table)
        }

        private fun writeAiAnalysis(aiSummary: String) {
            document.add(sectionTitle("AI 결산 분석"))
            document.add(
                Paragraph(aiSummary)
                    .setFontSize(10f)
                    .setMarginBottom(20f)
                    .setPaddingLeft(10f)
                    .setBorderLeft(SolidBorder(ACCENT, 3f)),
            )
        }

        private fun writeSpendings(snapshot: ReportSnapshot) {
            document.add(sectionTitle("지출 내역"))

            if (snapshot.spendings.isEmpty()) {
                document.add(Paragraph("승인된 지출이 없습니다.").setFontSize(10f).setFontColor(MUTED))
                return
            }

            // 태그 코드 대신 한글 라벨을 쓴다. 라벨은 tagTotals가 이미 갖고 있다.
            val labels = snapshot.tagTotals.associate { it.tag to it.label }
            val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 4f, 2f, 2f, 2f)))
                .useAllAvailableWidth()

            table.addHeaderCell(headerCell("일시", TextAlignment.LEFT))
            table.addHeaderCell(headerCell("항목", TextAlignment.LEFT))
            table.addHeaderCell(headerCell("분류", TextAlignment.LEFT))
            table.addHeaderCell(headerCell("결제자", TextAlignment.LEFT))
            table.addHeaderCell(headerCell("금액", TextAlignment.RIGHT))

            snapshot.spendings.forEach {
                table.addCell(bodyCell(it.spentAt.format(DATE_TIME), TextAlignment.LEFT))
                table.addCell(bodyCell(it.description, TextAlignment.LEFT))
                table.addCell(bodyCell(labels[it.tag] ?: it.tag, TextAlignment.LEFT))
                table.addCell(bodyCell(it.payerName, TextAlignment.LEFT))
                table.addCell(bodyCell(money(it.amount), TextAlignment.RIGHT))
            }

            document.add(table)
        }

        private fun sectionTitle(text: String): Paragraph =
            Paragraph(text).setFont(bold).setFontSize(13f).setMarginBottom(8f)

        private fun summaryLabel(text: String): Cell = Cell()
            .add(Paragraph(text).setFontSize(9f).setFontColor(MUTED))
            .setBorder(Border.NO_BORDER)
            .setPaddingBottom(2f)

        private fun summaryValue(amount: Long, color: DeviceRgb = PRIMARY): Cell = Cell()
            .add(Paragraph(money(amount)).setFont(bold).setFontSize(16f).setFontColor(color))
            .setBorder(Border.NO_BORDER)

        private fun headerCell(text: String, alignment: TextAlignment): Cell = Cell()
            .add(Paragraph(text).setFont(bold).setFontSize(9f))
            .setTextAlignment(alignment)
            .setBackgroundColor(HEADER_BACKGROUND)
            .setPadding(6f)

        private fun bodyCell(text: String, alignment: TextAlignment): Cell = Cell()
            .add(Paragraph(text).setFontSize(9f))
            .setTextAlignment(alignment)
            .setPadding(6f)

        private fun money(amount: Long): String = MONEY_FORMAT.format(amount) + "원"
    }

    companion object {
        const val CONTENT_TYPE = "application/pdf"

        private const val REGULAR_FONT_PATH = "fonts/NanumGothic-Regular.ttf"
        private const val BOLD_FONT_PATH = "fonts/NanumGothic-Bold.ttf"

        // 피그마 시안의 색을 따른다.
        private val PRIMARY = DeviceRgb(64, 58, 107)
        private val ACCENT = DeviceRgb(232, 168, 124)
        private val DANGER = DeviceRgb(200, 92, 92)
        private val MUTED = DeviceRgb(138, 138, 134)
        private val HEADER_BACKGROUND = DeviceRgb(238, 240, 242)

        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
        private val MONEY_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

        /** TTF 파일 내용. 디스크 읽기를 매번 하지 않도록 바이트만 캐시한다. */
        private val regularFontBytes: ByteArray by lazy { readFontBytes(REGULAR_FONT_PATH) }
        private val boldFontBytes: ByteArray by lazy { readFontBytes(BOLD_FONT_PATH) }

        private fun readFontBytes(path: String): ByteArray =
            ClassPathResource(path).inputStream.use { it.readBytes() }

        /**
         * iText 기본 폰트로는 한글을 렌더링할 수 없어 TTF를 직접 로드한다.
         * IDENTITY_H는 유니코드를 2바이트로 인코딩해 한글을 표현할 수 있게 하는 인코딩이다.
         */
        private fun createFont(bytes: ByteArray): PdfFont =
            PdfFontFactory.createFont(
                bytes,
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
            )
    }
}
