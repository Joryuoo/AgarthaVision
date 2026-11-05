package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.agarthavision.R
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportPdfHeader
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.repository.PatientReportPdfRenderer
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
        cursor.newPageIfNeeded(PdfChrome.LINE_HEIGHT * 2)
        cursor.y = PdfChrome.drawSectionHeader(
            cursor.canvas,
            context.getString(R.string.patient_report_pdf_session_table_title),
            cursor.y,
        )
        drawSessionTableHeader(cursor)

        val dateFormatter = DateTimeFormatter.ofPattern(SCOPE_DATE_PATTERN).withZone(ZoneId.systemDefault())
        val rowPaint = PdfChrome.paintFor(PdfChrome.TextStyle.VALUE)
        rows.forEach { row ->
            val wasNewPage = cursor.newPageIfNeeded(PdfChrome.LINE_HEIGHT)
            if (wasNewPage) drawSessionTableHeader(cursor)

            cursor.canvas.drawText(dateFormatter.format(row.startedAt), PdfChrome.MARGIN, cursor.y, rowPaint)
            cursor.canvas.drawText(
                row.sessionLabel ?: row.sessionId.take(SESSION_ID_TAKE),
                SESSION_COLUMN_X,
                cursor.y,
                rowPaint,
            )
            cursor.canvas.drawText(row.sampleCount.toString(), SAMPLES_COLUMN_X, cursor.y, rowPaint)
            cursor.canvas.drawText(row.eggsConfirmed.toString(), EGGS_COLUMN_X, cursor.y, rowPaint)
            val findings = if (row.positiveSpecies.isEmpty()) {
                context.getString(R.string.patient_report_pdf_session_negative)
            } else {
                row.positiveSpecies.joinToString(SEMICOLON_SEPARATOR) { species ->
                    val density = row.lpfPerSpecies[species]
                    if (density != null) "$species %d–%d".format(density.min, density.max) else species
                }
            }
            val maxFindingsWidth = PdfChrome.PAGE_WIDTH - FINDINGS_COLUMN_X - PdfChrome.MARGIN
            cursor.canvas.drawText(
                PdfChrome.truncate(rowPaint, findings, maxFindingsWidth),
                FINDINGS_COLUMN_X,
                cursor.y,
                rowPaint,
            )
            cursor.y += PdfChrome.LINE_HEIGHT
        }
    }

    private fun drawSessionTableHeader(cursor: PageCursor) {
        val headerPaint = PdfChrome.paintFor(PdfChrome.TextStyle.TABLE_HEADER)
        val dateLabel = context.getString(R.string.patient_report_pdf_column_date)
        val sessionLabel = context.getString(R.string.patient_report_pdf_column_session)
        val samplesLabel = context.getString(R.string.patient_report_pdf_column_samples)
        val eggsLabel = context.getString(R.string.patient_report_pdf_column_eggs)
        val findingsLabel = context.getString(R.string.patient_report_pdf_column_findings)
        cursor.canvas.drawText(dateLabel, PdfChrome.MARGIN, cursor.y, headerPaint)
        cursor.canvas.drawText(sessionLabel, SESSION_COLUMN_X, cursor.y, headerPaint)
        cursor.canvas.drawText(samplesLabel, SAMPLES_COLUMN_X, cursor.y, headerPaint)
        cursor.canvas.drawText(eggsLabel, EGGS_COLUMN_X, cursor.y, headerPaint)
        cursor.canvas.drawText(findingsLabel, FINDINGS_COLUMN_X, cursor.y, headerPaint)
        cursor.y += PdfChrome.LINE_HEIGHT
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
        simY += HERO_LABEL_TO_CARD_GAP + heroHeight + PdfChrome.SECTION_GAP

        val tableHeaderHeight = PdfChrome.LINE_HEIGHT * 2
        if (simY + tableHeaderHeight > maxContentY) {
            simPage++
            simY = PdfChrome.MARGIN + tableHeaderHeight
        } else {
            simY += tableHeaderHeight
        }

        document.sessionRows.forEach { _ ->
            if (simY + PdfChrome.LINE_HEIGHT > maxContentY) {
                simPage++
                simY = PdfChrome.MARGIN + PdfChrome.LINE_HEIGHT * 2
            } else {
                simY += PdfChrome.LINE_HEIGHT
            }
        }

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
        private const val SEMICOLON_SEPARATOR = "; "

        private const val SESSION_COLUMN_X = 110f
        private const val SAMPLES_COLUMN_X = 230f
        private const val EGGS_COLUMN_X = 290f
        private const val FINDINGS_COLUMN_X = 340f

        private const val FOOTER_RESERVE = 24f
        private const val LETTERHEAD_SIM_HEIGHT = 52f
        private const val TITLE_SIM_HEIGHT = 48f
        private const val META_CARD_SIM_HEIGHT = 84f
        private const val HERO_LABEL_TO_CARD_GAP = 8f
    }
}
