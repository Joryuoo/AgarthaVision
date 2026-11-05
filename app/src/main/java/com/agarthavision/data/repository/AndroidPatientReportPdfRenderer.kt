package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.agarthavision.R
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportPdfHeader
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.repository.PatientReportPdfRenderer
import com.agarthavision.ui.theme.AppColors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders [PatientReportPdfDocument] to a paginated A4 PDF using `android.graphics.pdf.PdfDocument`.
 *
 * Page 1 renders the redesigned letterhead, title block, 2×2 metadata card, pooled findings maroon
 * hero card, followed by the paginated per-session breakdown table and reviewer sign-off block.
 * When sample images are included, 2×2 image appendix pages are appended.
 */
@Suppress("MagicNumber")
class AndroidPatientReportPdfRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PatientReportPdfRenderer {

    override suspend fun render(document: PatientReportPdfDocument): ByteArray {
        val summaryPages = calculateSummaryPagesCount(document)
        val appendixPages = if (document.appendixSamples.isEmpty()) {
            0
        } else {
            (document.appendixSamples.size + PdfChrome.SAMPLES_PER_APPENDIX_PAGE - 1) /
                PdfChrome.SAMPLES_PER_APPENDIX_PAGE
        }
        val totalPages = summaryPages + appendixPages
        val appendixStartPage = summaryPages + 1

        val pdfDocument = PdfDocument()
        val cursor = PageCursor(pdfDocument, totalPages, document.header.reportId)
        cursor.startPage()

        drawSummaryFirstPage(cursor, document, appendixStartPage)
        drawSessionTable(cursor, document.sessionRows)
        drawSignoff(cursor, document.header)
        cursor.finishPage(isAppendix = false)

        if (document.appendixSamples.isNotEmpty()) {
            val chunks = document.appendixSamples.chunked(PdfChrome.SAMPLES_PER_APPENDIX_PAGE)
            chunks.forEach { chunk ->
                cursor.startPage()
                PdfChrome.drawAppendixPage(
                    context = context,
                    canvas = cursor.canvas,
                    samplesChunk = chunk,
                    patientName = document.header.patientDisplayName,
                    sessionOrScopeLabel = formatScope(document.header),
                    organizationName = document.header.organizationName,
                    footerParams = RedesignedFooterParams(
                        reportId = document.header.reportId,
                        pageNumber = cursor.pageNumber,
                        totalPages = totalPages,
                        isAppendix = true,
                    ),
                )
                pdfDocument.finishPage(cursor.page)
            }
        }

        return ByteArrayOutputStream().use { output ->
            pdfDocument.writeTo(output)
            pdfDocument.close()
            output.toByteArray()
        }
    }

    private fun drawSummaryFirstPage(
        cursor: PageCursor,
        document: PatientReportPdfDocument,
        appendixStartPage: Int,
    ) {
        val letterheadBottom = PdfChrome.drawLetterhead(context, cursor.canvas, document.header.organizationName)

        val subtitle = if (document.appendixSamples.isNotEmpty()) {
            context.getString(R.string.report_pdf_subtitle_with_appendix, appendixStartPage)
        } else {
            context.getString(R.string.report_pdf_subtitle_standard)
        }
        val titleBottom = PdfChrome.drawReportTitle(
            canvas = cursor.canvas,
            title = context.getString(R.string.report_pdf_main_title),
            subtitle = subtitle,
            startY = letterheadBottom,
        )

        val sexLabel = context.getString(PdfChrome.sexLabelRes(document.header.sex))
        val ageValue = context.getString(R.string.report_pdf_age_value, document.header.ageYears)
        val reportDate = DateTimeFormatter.ofPattern(DATE_PATTERN)
            .withZone(ZoneId.systemDefault())
            .format(document.header.generatedAt)
        val sessionsScope = "${document.header.totalSessions} · ${formatScope(document.header)}"
        val metadataCells = listOf(
            context.getString(R.string.report_pdf_meta_patient) to document.header.patientDisplayName,
            context.getString(R.string.report_pdf_meta_sessions_included) to sessionsScope,
            context.getString(R.string.report_pdf_meta_patient_details_address) to
                "$sexLabel · $ageValue · ${document.header.address}",
            context.getString(R.string.report_pdf_meta_report_date) to reportDate,
        )
        val metaBottom = PdfChrome.drawMetadataCard(cursor.canvas, titleBottom, metadataCells, numRows = 2)

        val emptyMessage = context.resources.getQuantityString(
            R.plurals.patient_report_pdf_no_parasites_sessions,
            document.header.totalSessions,
            document.header.totalSessions,
        )
        val heroParams = HeroCardParams(
            totalEggsConfirmed = document.header.totalEggsConfirmed,
            speciesCount = document.header.positiveSpecies.size,
            fieldsExamined = document.header.totalSamples,
            totalSessions = document.header.totalSessions,
            speciesRows = document.speciesRows,
            emptyMessage = emptyMessage,
        )
        cursor.y = PdfChrome.drawMicroscopyHeroCard(context, cursor.canvas, heroParams, metaBottom)
    }

    private fun drawSessionTable(cursor: PageCursor, rows: List<PatientReportSessionRow>) {
        val titlePaint = PdfChrome.pdfPaint(
            color = AppColors.Maroon,
            textSize = TABLE_TITLE_SIZE,
            weight = 700,
            letterSpacing = 0.08f,
        )
        val titleBaseline = cursor.y + TABLE_TITLE_BASELINE_OFFSET
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_session_table_title).uppercase(),
            PdfChrome.MARGIN,
            titleBaseline,
            titlePaint,
        )
        cursor.y = titleBaseline + TABLE_TITLE_ADVANCE_OFFSET

        drawSessionTableHeader(cursor)

        val dateFormatter = DateTimeFormatter.ofPattern(SCOPE_DATE_PATTERN).withZone(ZoneId.systemDefault())
        val cellPaint = PdfChrome.pdfPaint(
            color = AppColors.Gray900,
            textSize = ROW_TEXT_SIZE,
            weight = 400,
            letterSpacing = 0.01f,
        )
        val findingsPaint = PdfChrome.pdfPaint(
            color = AppColors.Gray900,
            textSize = ROW_TEXT_SIZE,
            weight = 500,
            letterSpacing = 0.01f,
        )
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = ROW_BORDER_WIDTH
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            strokeWidth = ROW_BORDER_WIDTH
        }
        val whiteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.White.toArgb()
            style = Paint.Style.FILL
        }
        val warmBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Warm50.toArgb()
            style = Paint.Style.FILL
        }

        val maxFindingsWidth = PAGE_RIGHT - FINDINGS_COLUMN_X - FINDINGS_RIGHT_MARGIN

        rows.forEachIndexed { index, row ->
            val findingLines = formatFindingLines(row, findingsPaint, maxFindingsWidth)
            val rowHeight = maxOf(1, findingLines.size) * ROW_LINE_HEIGHT + ROW_PADDING_VERTICAL
            val wasNewPage = cursor.newPageIfNeeded(rowHeight)
            if (wasNewPage) drawSessionTableHeader(cursor)

            val rowRect = RectF(PdfChrome.MARGIN, cursor.y, PAGE_RIGHT, cursor.y + rowHeight)
            val bgPaint = if (index % 2 == 1) warmBgPaint else whiteBgPaint
            cursor.canvas.drawRect(rowRect, bgPaint)
            cursor.canvas.drawRect(rowRect, borderPaint)

            DIVIDER_XS.forEach { divX ->
                cursor.canvas.drawLine(divX, cursor.y, divX, cursor.y + rowHeight, dividerPaint)
            }

            val baseBaseline = cursor.y + ROW_BASELINE_OFFSET
            cursor.canvas.drawText(dateFormatter.format(row.startedAt), DATE_COLUMN_X, baseBaseline, cellPaint)
            cursor.canvas.drawText(
                row.sessionLabel ?: row.sessionId.take(SESSION_ID_TAKE),
                SESSION_COLUMN_X,
                baseBaseline,
                cellPaint,
            )
            cursor.canvas.drawText(row.sampleCount.toString(), SAMPLES_COLUMN_X, baseBaseline, cellPaint)
            cursor.canvas.drawText(row.eggsConfirmed.toString(), EGGS_COLUMN_X, baseBaseline, cellPaint)

            findingLines.forEachIndexed { lineIdx, line ->
                val lineBaseline = baseBaseline + lineIdx * ROW_LINE_HEIGHT
                cursor.canvas.drawText(line, FINDINGS_COLUMN_X, lineBaseline, findingsPaint)
            }

            cursor.y += rowHeight
        }
        cursor.y += TABLE_BOTTOM_GAP
    }

    private fun drawSessionTableHeader(cursor: PageCursor) {
        val headerRect = RectF(PdfChrome.MARGIN, cursor.y, PAGE_RIGHT, cursor.y + TABLE_HEADER_HEIGHT)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Warm100.toArgb()
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = ROW_BORDER_WIDTH
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            strokeWidth = ROW_BORDER_WIDTH
        }
        cursor.canvas.drawRect(headerRect, bgPaint)
        cursor.canvas.drawRect(headerRect, borderPaint)

        DIVIDER_XS.forEach { divX ->
            cursor.canvas.drawLine(divX, cursor.y, divX, cursor.y + TABLE_HEADER_HEIGHT, dividerPaint)
        }

        val headerTextPaint = PdfChrome.pdfPaint(
            color = AppColors.Gray700,
            textSize = TABLE_HEADER_TEXT_SIZE,
            weight = 700,
            letterSpacing = 0.03f,
        )
        val textBaseline = cursor.y + TABLE_HEADER_BASELINE_OFFSET
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_column_date),
            DATE_COLUMN_X,
            textBaseline,
            headerTextPaint,
        )
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_column_session),
            SESSION_COLUMN_X,
            textBaseline,
            headerTextPaint,
        )
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_column_samples),
            SAMPLES_COLUMN_X,
            textBaseline,
            headerTextPaint,
        )
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_column_eggs),
            EGGS_COLUMN_X,
            textBaseline,
            headerTextPaint,
        )
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_column_findings),
            FINDINGS_COLUMN_X,
            textBaseline,
            headerTextPaint,
        )

        cursor.y += TABLE_HEADER_HEIGHT
    }

    private fun formatFindingLines(
        row: PatientReportSessionRow,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        if (row.positiveSpecies.isEmpty()) {
            return listOf(context.getString(R.string.patient_report_pdf_session_negative))
        }
        return row.positiveSpecies.flatMap { species ->
            formatSpeciesLines(species, row.lpfPerSpecies[species], paint, maxWidth)
        }
    }

    private fun formatSpeciesLines(
        species: String,
        density: LpfDensity?,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        val text = if (density != null) "$species ${density.min}–${density.max} LPF" else species
        if (paint.measureText(text) <= maxWidth) {
            return listOf(text)
        }
        return splitTextLines(text, paint, maxWidth)
    }

    private fun splitTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) {
                lines.add(remaining)
                break
            }
            val cut = if (count < remaining.length && remaining.lastIndexOf(' ', count) > 0) {
                remaining.lastIndexOf(' ', count)
            } else {
                count
            }
            lines.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trim()
        }
        return lines
    }

    private fun drawSignoff(cursor: PageCursor, header: PatientReportPdfHeader) {
        val signoffHeight = PdfChrome.calculateSignoffHeight(
            header.organizationName,
            header.generatedByEmail,
        )
        cursor.newPageIfNeeded(signoffHeight)
        val signoffParams = ReviewerSignoffParams(
            reviewerName = header.generatedBy,
            organizationName = header.organizationName,
            reviewerEmail = header.generatedByEmail,
        )
        cursor.y = PdfChrome.drawReviewerSignoff(context, cursor.canvas, signoffParams, cursor.y)
    }

    private fun calculateSummaryPagesCount(document: PatientReportPdfDocument): Int {
        val maxContentY = PdfChrome.PAGE_HEIGHT - PdfChrome.MARGIN - FOOTER_RESERVE
        var simPage = 1
        var simY = PdfChrome.MARGIN

        simY += LETTERHEAD_SIM_HEIGHT
        simY += TITLE_SIM_HEIGHT
        simY += META_CARD_SIM_HEIGHT
        val heroHeight = PdfChrome.calculateHeroCardHeight(document.speciesRows.size)
        simY += HERO_SECTION_TITLE_HEIGHT + heroHeight + HERO_GAP_AFTER

        val tableHeaderBlockHeight = TABLE_TITLE_BLOCK_HEIGHT + TABLE_HEADER_HEIGHT
        if (simY + tableHeaderBlockHeight > maxContentY) {
            simPage++
            simY = PdfChrome.MARGIN + tableHeaderBlockHeight
        } else {
            simY += tableHeaderBlockHeight
        }

        val findingsPaint = PdfChrome.pdfPaint(
            color = AppColors.Gray900,
            textSize = ROW_TEXT_SIZE,
            weight = 500,
            letterSpacing = 0.01f,
        )
        val maxFindingsWidth = PAGE_RIGHT - FINDINGS_COLUMN_X - FINDINGS_RIGHT_MARGIN

        document.sessionRows.forEach { row ->
            val findingLines = formatFindingLines(row, findingsPaint, maxFindingsWidth)
            val rowHeight = maxOf(1, findingLines.size) * ROW_LINE_HEIGHT + ROW_PADDING_VERTICAL
            if (simY + rowHeight > maxContentY) {
                simPage++
                simY = PdfChrome.MARGIN + TABLE_HEADER_HEIGHT + rowHeight
            } else {
                simY += rowHeight
            }
        }

        simY += TABLE_BOTTOM_GAP

        val signoffHeight = PdfChrome.calculateSignoffHeight(
            document.header.organizationName,
            document.header.generatedByEmail,
        )
        if (simY + signoffHeight > maxContentY) {
            simPage++
        }

        return simPage
    }

    private fun formatScope(header: PatientReportPdfHeader): String {
        val dateFormatter = DateTimeFormatter.ofPattern(SCOPE_DATE_PATTERN)
        val start = header.scopeStart?.format(dateFormatter)
        val end = header.scopeEnd?.format(dateFormatter)
        return when {
            start != null && end != null -> context.getString(R.string.patient_report_pdf_scope_range, start, end)
            start != null -> context.getString(R.string.patient_report_pdf_scope_from, start)
            end != null -> context.getString(R.string.patient_report_pdf_scope_until, end)
            else -> context.getString(R.string.patient_report_pdf_scope_all_sessions)
        }
    }

    /**
     * Owns the current [PdfDocument.Page]/[Canvas]/cursor-y and turns the page when content
     * would not fit above the footer, drawing the finished page's footer before starting the
     * next one.
     */
    private inner class PageCursor(
        private val pdfDocument: PdfDocument,
        private val totalPages: Int,
        private val reportId: String,
    ) {
        var pageNumber = 0
            private set
        var y = PdfChrome.MARGIN
        lateinit var page: PdfDocument.Page
            private set
        val canvas: Canvas get() = page.canvas

        fun startPage() {
            pageNumber++
            val pageInfo = PdfDocument.PageInfo
                .Builder(PdfChrome.PAGE_WIDTH, PdfChrome.PAGE_HEIGHT, pageNumber)
                .create()
            page = pdfDocument.startPage(pageInfo)
            y = PdfChrome.MARGIN
        }

        /** Finishes the current page after drawing its footer. */
        fun finishPage(isAppendix: Boolean = false) {
            val footerParams = RedesignedFooterParams(
                reportId = reportId,
                pageNumber = pageNumber,
                totalPages = totalPages,
                isAppendix = isAppendix,
            )
            PdfChrome.drawRedesignedFooter(context, canvas, footerParams)
            pdfDocument.finishPage(page)
        }

        /**
         * Ends the current page and starts a fresh one when [requiredHeight] would not fit above
         * the footer.
         *
         * @return true when a new page was started, so the caller knows to redraw column headers.
         */
        fun newPageIfNeeded(requiredHeight: Float): Boolean {
            if (y + requiredHeight <= PdfChrome.PAGE_HEIGHT - PdfChrome.MARGIN - FOOTER_RESERVE) return false
            finishPage(isAppendix = false)
            startPage()
            return true
        }
    }

    private companion object {
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
        private const val SCOPE_DATE_PATTERN = "yyyy-MM-dd"
        private const val SESSION_ID_TAKE = 8

        private const val TABLE_TITLE_SIZE = 9.5f
        private const val TABLE_TITLE_BASELINE_OFFSET = 10f
        private const val TABLE_TITLE_ADVANCE_OFFSET = 10f
        private const val TABLE_TITLE_BLOCK_HEIGHT = 20f

        private const val TABLE_HEADER_HEIGHT = 26f
        private const val TABLE_HEADER_TEXT_SIZE = 9.5f
        private const val TABLE_HEADER_BASELINE_OFFSET = 17f

        private const val ROW_PADDING_VERTICAL = 10f
        private const val ROW_LINE_HEIGHT = 14f
        private const val ROW_BASELINE_OFFSET = 16f
        private const val ROW_BORDER_WIDTH = 1f
        private const val ROW_TEXT_SIZE = 9.5f
        private const val TABLE_BOTTOM_GAP = 16f
        private const val FINDINGS_RIGHT_MARGIN = 12f

        private const val PAGE_RIGHT = PdfChrome.PAGE_WIDTH - PdfChrome.MARGIN
        private const val DATE_COLUMN_X = 52f
        private const val SESSION_COLUMN_X = 128f
        private const val SAMPLES_COLUMN_X = 224f
        private const val EGGS_COLUMN_X = 276f
        private const val FINDINGS_COLUMN_X = 322f
        private val DIVIDER_XS = floatArrayOf(124f, 220f, 272f, 318f)

        private const val FOOTER_RESERVE = 16f
        private const val LETTERHEAD_SIM_HEIGHT = 66f
        private const val TITLE_SIM_HEIGHT = 56f
        private const val META_CARD_SIM_HEIGHT = 110f
        private const val HERO_SECTION_TITLE_HEIGHT = 20f
        private const val HERO_GAP_AFTER = 24f
    }
}
