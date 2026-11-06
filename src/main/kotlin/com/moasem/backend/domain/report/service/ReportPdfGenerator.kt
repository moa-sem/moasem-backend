package com.moasem.backend.domain.report.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.otf.GlyphLine
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Div
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.BorderCollapsePropertyValue
import com.itextpdf.layout.properties.BorderRadius
import com.itextpdf.layout.properties.Property
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import com.itextpdf.layout.splitting.ISplitCharacters
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
 * 모임 구성원끼리 공유하고 보관하는 결산서다. 요약을 앞에 두고, 근거가 되는 예산 내역과
 * 지출 내역을 뒤에 둔다. 표는 가로줄만 얇게 써서 숫자가 먼저 읽히게 한다.
 *
 * AI 분석은 선택 사항이다. aiSummary가 null이면 해당 섹션만 빠지고 나머지는 그대로 만들어진다.
 *
 * 문서 안에 "지금 시각"을 넣지 않는다. 같은 스냅샷이면 언제 만들어도 같은 내용이어야 한다.
 */
@Component
class ReportPdfGenerator {

    fun generate(snapshot: ReportSnapshot, aiSummary: String? = null): ByteArray {
        val body = ByteArrayOutputStream()

        PdfDocument(PdfWriter(body)).use { pdf ->
            Document(pdf, PageSize.A4).use { document ->
                document.setMargins(MARGIN_TOP, MARGIN_SIDE, MARGIN_BOTTOM, MARGIN_SIDE)
                Renderer(document).render(snapshot, aiSummary)
            }
        }

        return stampFooter(body.toByteArray(), snapshot)
    }

    /**
     * 모든 페이지에 꼬리말을 찍는다.
     *
     * "현재 페이지 / 전체 페이지"를 쓰려면 전체 페이지 수를 알아야 하는데, 본문을 다 그리기
     * 전에는 알 수 없다. 그래서 본문을 먼저 만들고 한 번 더 열어 꼬리말만 덧입힌다.
     */
    private fun stampFooter(body: ByteArray, snapshot: ReportSnapshot): ByteArray {
        val output = ByteArrayOutputStream()

        PdfDocument(PdfReader(body.inputStream()), PdfWriter(output)).use { pdf ->
            val font = createFont(regularFontBytes)
            val total = pdf.numberOfPages
            val caption = "모아셈 결산 보고서  ·  ${snapshot.event.groupName}  ·  ${snapshot.event.title}"

            for (number in 1..total) {
                val page = pdf.getPage(number)
                val size = page.pageSize
                val y = MARGIN_BOTTOM - 26f

                PdfCanvas(page).apply {
                    setStrokeColor(LINE)
                    setLineWidth(0.6f)
                    moveTo(MARGIN_SIDE.toDouble(), (y + 14f).toDouble())
                    lineTo((size.width - MARGIN_SIDE).toDouble(), (y + 14f).toDouble())
                    stroke()
                }

                Canvas(PdfCanvas(page), size).use { canvas ->
                    canvas.setFont(font).setFontSize(8f).setFontColor(MUTED)
                    canvas.showTextAligned(caption, MARGIN_SIDE, y, TextAlignment.LEFT)
                    canvas.showTextAligned("$number / $total", size.width - MARGIN_SIDE, y, TextAlignment.RIGHT)
                }
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
            document.setFont(regular).setFontColor(TEXT)
            // 한글은 음절마다 줄을 바꿀 수 있어 iText가 단어 중간에서 끊는다("교통비" → "교" / "통비").
            // 띄어쓰기에서만 끊도록 바꾼다. 하위 요소가 이 설정을 물려받는다.
            document.setProperty(Property.SPLIT_CHARACTERS, WordBoundarySplit)

            writeHeader(snapshot)
            writeSummary(snapshot)
            writeBudgetBreakdown(snapshot)
            writeTagTotals(snapshot)
            aiSummary?.let { writeAiAnalysis(it) }
            writeSpendings(snapshot)
        }

        private fun writeHeader(snapshot: ReportSnapshot) {
            val event = snapshot.event

            document.add(
                Paragraph("MOASEM  결산 보고서")
                    .setFont(bold)
                    .setFontSize(8.5f)
                    .setFontColor(PRIMARY)
                    .setCharacterSpacing(0.6f)
                    .setMarginBottom(6f),
            )
            document.add(
                Paragraph(event.title)
                    .setFont(bold)
                    .setFontSize(20f)
                    .setMarginBottom(4f),
            )

            val subtitle = buildList {
                add(event.groupName)
                add("${event.startAt.format(DATE)} ~ ${event.endAt.format(DATE)}")
                event.participantCount?.let { add("참여 ${it}명") }
            }.joinToString("   ·   ")

            document.add(Paragraph(subtitle).setFontSize(9.5f).setFontColor(MUTED).setMarginBottom(12f))
            document.add(LineSeparator(SolidLine(0.8f).apply { color = LINE }).setMarginBottom(14f))
        }

        private fun writeSummary(snapshot: ReportSnapshot) {
            val budget = snapshot.budget
            val isOver = budget.remainingBalance < 0

            val cards = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
                .useAllAvailableWidth()
                .setBorderCollapse(BorderCollapsePropertyValue.SEPARATE)
                .setHorizontalBorderSpacing(8f)
                .setMarginBottom(8f)

            cards.addCell(summaryCard("총 예산", budget.totalBudget, TEXT))
            cards.addCell(summaryCard("총 지출", budget.totalSpent, TEXT))
            // 예산을 넘겼으면 음수다. "남은 금액"이라고 부르면 초과 사실이 드러나지 않는다.
            cards.addCell(
                summaryCard(
                    if (isOver) "초과 금액" else "남은 금액",
                    budget.remainingBalance,
                    if (isOver) DANGER else PRIMARY,
                ),
            )
            document.add(cards)

            if (budget.totalBudget > 0) writeUsageBar(budget.totalSpent, budget.totalBudget, isOver)
        }

        /** 예산 대비 집행률. 넘쳤으면 막대를 꽉 채우고 붉게 칠해 한눈에 보이게 한다. */
        private fun writeUsageBar(spent: Long, total: Long, isOver: Boolean) {
            val rate = spent.toDouble() / total * 100
            val row = Table(UnitValue.createPercentArray(floatArrayOf(5f, 1.3f)))
                .useAllAvailableWidth()
                .setMarginBottom(16f)

            row.addCell(
                plainCell().setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .add(bar(rate.coerceAtMost(100.0), if (isOver) DANGER else PRIMARY)),
            )
            row.addCell(
                plainCell().setTextAlignment(TextAlignment.RIGHT)
                    .add(
                        Paragraph("집행률 ${PERCENT.format(rate)}%")
                            .setFont(bold).setFontSize(9f)
                            .setFontColor(if (isOver) DANGER else MUTED),
                    ),
            )
            document.add(row)
        }

        private fun writeBudgetBreakdown(snapshot: ReportSnapshot) {
            val budget = snapshot.budget
            document.add(sectionTitle("예산 내역"))

            val table = lightTable(floatArrayOf(1.4f, 4f, 2.2f, 2f))
            table.addHeaderCell(headerCell("구분"))
            table.addHeaderCell(headerCell("내용"))
            table.addHeaderCell(headerCell("등록"))
            table.addHeaderCell(headerCell("금액", TextAlignment.RIGHT))

            table.addCell(bodyCell("최초 예산"))
            table.addCell(bodyCell("행사 생성 시 편성", MUTED))
            table.addCell(bodyCell("-", MUTED))
            table.addCell(bodyCell(money(budget.initialBudget), alignment = TextAlignment.RIGHT))

            budget.additions.forEach {
                val registered = listOfNotNull(it.addedBy, it.addedAt.format(SHORT_DATE)).joinToString(" · ")
                table.addCell(bodyCell("추가"))
                table.addCell(bodyCell(it.reason))
                table.addCell(bodyCell(registered, MUTED))
                table.addCell(bodyCell("+${money(it.amount)}", alignment = TextAlignment.RIGHT))
            }

            addTotalRow(table, "총 예산", budget.totalBudget, columns = 4)
            document.add(table.setMarginBottom(16f))
        }

        private fun writeTagTotals(snapshot: ReportSnapshot) {
            if (snapshot.tagTotals.isEmpty()) return
            val totalSpent = snapshot.budget.totalSpent

            document.add(sectionTitle("항목별 지출"))

            val table = lightTable(floatArrayOf(2.2f, 1f, 2.2f, 1.3f, 3.3f))
            table.addHeaderCell(headerCell("항목"))
            table.addHeaderCell(headerCell("건수", TextAlignment.RIGHT))
            table.addHeaderCell(headerCell("금액", TextAlignment.RIGHT))
            table.addHeaderCell(headerCell("비중", TextAlignment.RIGHT))
            table.addHeaderCell(headerCell(""))

            snapshot.tagTotals.forEach {
                val share = if (totalSpent > 0) it.amount.toDouble() / totalSpent * 100 else 0.0
                table.addCell(bodyCell(it.label))
                table.addCell(bodyCell("${it.count}건", MUTED, TextAlignment.RIGHT))
                table.addCell(bodyCell(money(it.amount), alignment = TextAlignment.RIGHT))
                table.addCell(bodyCell("${PERCENT.format(share)}%", MUTED, TextAlignment.RIGHT))
                table.addCell(rowCell().setVerticalAlignment(VerticalAlignment.MIDDLE).add(bar(share, ACCENT)))
            }

            document.add(table.setMarginBottom(16f))
        }

        private fun writeAiAnalysis(aiSummary: String) {
            document.add(sectionTitle("AI 결산 분석"))

            val box = Div()
                .setBackgroundColor(ACCENT_TINT)
                .setBorderLeft(SolidBorder(ACCENT, 3f))
                .setPadding(10f)
                .setMarginBottom(16f)

            box.add(Paragraph(aiSummary).setFontSize(9.5f).setFixedLeading(15f).setMarginBottom(6f))
            box.add(
                Paragraph("결산 수치를 바탕으로 AI가 작성한 요약입니다. 금액은 위 표를 기준으로 합니다.")
                    .setFontSize(7.5f)
                    .setFontColor(MUTED),
            )
            document.add(box)
        }

        private fun writeSpendings(snapshot: ReportSnapshot) {
            document.add(sectionTitle("지출 내역"))

            if (snapshot.spendings.isEmpty()) {
                document.add(Paragraph("승인된 지출이 없습니다.").setFontSize(9.5f).setFontColor(MUTED))
                return
            }

            // 태그 코드 대신 한글 라벨을 쓴다. 라벨은 tagTotals가 이미 갖고 있다.
            val labels = snapshot.tagTotals.associate { it.tag to it.label }
            val table = lightTable(floatArrayOf(1.2f, 3.6f, 1.5f, 1.6f, 1.1f, 2f))

            table.addHeaderCell(headerCell("날짜"))
            table.addHeaderCell(headerCell("내용"))
            table.addHeaderCell(headerCell("분류"))
            table.addHeaderCell(headerCell("결제자"))
            table.addHeaderCell(headerCell("증빙"))
            table.addHeaderCell(headerCell("금액", TextAlignment.RIGHT))

            snapshot.spendings.forEach {
                // 지출은 날짜만 입력받는다. 시각을 찍으면 모든 행이 00:00으로 나온다.
                table.addCell(bodyCell(it.spentAt.format(SHORT_DATE), MUTED))
                table.addCell(bodyCell(it.description))
                table.addCell(bodyCell(labels[it.tag] ?: it.tag, MUTED))
                table.addCell(bodyCell(it.payerName))
                table.addCell(bodyCell(if (it.receiptKey != null) "첨부" else "-", MUTED))
                table.addCell(bodyCell(money(it.amount), alignment = TextAlignment.RIGHT))
            }

            addTotalRow(table, "합계", snapshot.budget.totalSpent, columns = 6)
            document.add(table)
        }

        private fun summaryCard(label: String, amount: Long, color: DeviceRgb): Cell = Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(SURFACE)
            .setBorderRadius(BorderRadius(8f))
            .setPadding(10f)
            .add(Paragraph(label).setFontSize(8.5f).setFontColor(MUTED).setMarginBottom(4f))
            .add(Paragraph(money(amount)).setFont(bold).setFontSize(15f).setFontColor(color))

        /** 비율 막대. 0이나 100에서도 깨지지 않도록 채움 폭을 범위 안으로 묶는다. */
        private fun bar(percent: Double, color: DeviceRgb): Div {
            val fill = percent.coerceIn(0.0, 100.0).toFloat()
            val track = Div().setHeight(6f).setBackgroundColor(TRACK).setBorderRadius(BorderRadius(3f))
            if (fill > 0f) {
                track.add(
                    Div().setHeight(6f)
                        .setWidth(UnitValue.createPercentValue(fill))
                        .setBackgroundColor(color)
                        .setBorderRadius(BorderRadius(3f)),
                )
            }
            return track
        }

        private fun addTotalRow(table: Table, label: String, amount: Long, columns: Int) {
            table.addCell(
                totalCell().add(Paragraph(label).setFont(bold).setFontSize(9f))
                    .also { if (columns > 1) it.setTextAlignment(TextAlignment.LEFT) },
            )
            repeat(columns - 2) { table.addCell(totalCell()) }
            table.addCell(
                totalCell().setTextAlignment(TextAlignment.RIGHT)
                    .add(Paragraph(money(amount)).setFont(bold).setFontSize(9.5f)),
            )
        }

        private fun lightTable(widths: FloatArray): Table =
            Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()

        private fun sectionTitle(text: String): Paragraph =
            // 제목만 페이지 끝에 남고 표가 다음 장으로 넘어가는 것을 막는다.
            Paragraph(text).setFont(bold).setFontSize(12f).setMarginBottom(6f).setKeepWithNext(true)

        private fun headerCell(text: String, alignment: TextAlignment = TextAlignment.LEFT): Cell = Cell()
            .add(Paragraph(text).setFont(bold).setFontSize(8.5f).setFontColor(MUTED))
            .setTextAlignment(alignment)
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(SolidBorder(HEADER_LINE, 0.8f))
            .setPaddingTop(2f).setPaddingBottom(5f).setPaddingLeft(4f).setPaddingRight(4f)

        private fun bodyCell(
            text: String,
            color: DeviceRgb = TEXT,
            alignment: TextAlignment = TextAlignment.LEFT,
        ): Cell = rowCell()
            .add(Paragraph(text).setFontSize(9f).setFontColor(color))
            .setTextAlignment(alignment)

        private fun rowCell(): Cell = Cell()
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(SolidBorder(LINE, 0.6f))
            .setPaddingTop(5f).setPaddingBottom(5f).setPaddingLeft(4f).setPaddingRight(4f)

        private fun totalCell(): Cell = Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(SURFACE)
            .setPaddingTop(6f).setPaddingBottom(6f).setPaddingLeft(4f).setPaddingRight(4f)

        private fun plainCell(): Cell = Cell().setBorder(Border.NO_BORDER).setPadding(0f)

        private fun money(amount: Long): String = MONEY_FORMAT.format(amount) + "원"
    }

    /** 띄어쓰기에서만 줄을 바꾼다. 한 단어가 한 줄보다 길면 iText가 알아서 강제로 끊는다. */
    private object WordBoundarySplit : ISplitCharacters {
        override fun isSplitCharacter(text: GlyphLine, glyphPos: Int): Boolean {
            val unicode = text.get(glyphPos).unicode
            return unicode == ' '.code || unicode == '\t'.code || unicode == '\n'.code
        }
    }

    companion object {
        const val CONTENT_TYPE = "application/pdf"

        private const val REGULAR_FONT_PATH = "fonts/NanumGothic-Regular.ttf"
        private const val BOLD_FONT_PATH = "fonts/NanumGothic-Bold.ttf"

        private const val MARGIN_TOP = 44f
        private const val MARGIN_SIDE = 44f
        /** 꼬리말 자리를 남겨 둔다. 본문이 꼬리말을 덮지 않게 한다. */
        private const val MARGIN_BOTTOM = 64f

        // 앱 화면과 같은 색을 쓴다.
        private val PRIMARY = DeviceRgb(64, 58, 107)
        private val ACCENT = DeviceRgb(232, 168, 124)
        private val ACCENT_TINT = DeviceRgb(253, 245, 238)
        private val DANGER = DeviceRgb(200, 92, 92)
        private val TEXT = DeviceRgb(43, 43, 40)
        private val MUTED = DeviceRgb(138, 138, 134)
        private val LINE = DeviceRgb(232, 232, 228)
        private val HEADER_LINE = DeviceRgb(200, 200, 196)
        private val SURFACE = DeviceRgb(246, 246, 244)
        private val TRACK = DeviceRgb(236, 236, 240)

        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")
        private val MONEY_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
        private val PERCENT = java.text.DecimalFormat("0.0")

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
