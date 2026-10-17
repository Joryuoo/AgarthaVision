package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.agarthavision.R
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportPdfHeader
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.repository.PatientReportPdfRenderer
import com.agarthavision.ui.records.labelRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders [PatientReportPdfDocument] to a paginated PDF using `android.graphics.pdf.PdfDocument`.
 *
 * Unlike the single-page session report, a patient report can pool an unbounded number of
 * sessions, so the per-session breakdown table paginates: a new page starts once the next row
 * would not fit above the footer, repeating the table's column headers so a page read on its
 * own is still legible. Shared letterhead, typography and `label: value` drawing live in
 * [PdfChrome], alongside [AndroidReportPdfRenderer].
 */
class AndroidPatientReportPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : PatientReportPdfRenderer {

    override suspend fun render(document: PatientReportPdfDocument): ByteArray {
        val pdfDocument = PdfDocument()
        val cursor = PageCursor(pdfDocument)
        cursor.startPage()

        drawLetterhead(cursor)
        drawPatientBlock(cursor, document.header)
        cursor.y += PdfChrome.SECTION_GAP
        drawScopeBlock(cursor, document.header)
        cursor.y += PdfChrome.SECTION_GAP
        drawSummaryBlock(cursor, document.header)
        cursor.y += PdfChrome.SECTION_GAP
        drawPooledSpeciesTable(cursor, document.speciesRows, document.header.totalSessions)
        cursor.y += PdfChrome.SECTION_GAP
        drawSessionTable(cursor, document.sessionRows)

        cursor.finishPage()

        return ByteArrayOutputStream().use { output ->
            pdfDocument.writeTo(output)
            pdfDocument.close()
            output.toByteArray()
        }
    }

    private fun drawLetterhead(cursor: PageCursor) {
        val titleX = PdfChrome.drawLogo(context, cursor.canvas)
        val titlePaint = PdfChrome.paintFor(PdfChrome.TextStyle.TITLE)
        val metrics = titlePaint.fontMetrics
        val titleBaseline = PdfChrome.MARGIN + PdfChrome.LOGO_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        cursor.canvas.drawText(
            context.getString(R.string.patient_report_pdf_document_title),
            titleX,
            titleBaseline,
            titlePaint,
        )
        cursor.y = PdfChrome.MARGIN + PdfChrome.LOGO_SIZE + PdfChrome.SECTION_GAP
    }

    private fun drawPatientBlock(cursor: PageCursor, header: PatientReportPdfHeader) {
        cursor.y = PdfChrome.drawLabeledLines(
            cursor.canvas,
            listOf(
                context.getString(R.string.patient_report_pdf_patient_name_label) to header.patientDisplayName,
                context.getString(R.string.patient_report_pdf_sex_label) to
                    context.getString(PdfChrome.sexLabelRes(header.sex)),
                context.getString(R.string.patient_report_pdf_age_label) to
                    context.getString(R.string.report_pdf_age_value, header.ageYears),
                context.getString(R.string.patient_report_pdf_address_label) to header.address,
            ),
            cursor.y,
        )
    }

    private fun drawScopeBlock(cursor: PageCursor, header: PatientReportPdfHeader) {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(ZoneId.systemDefault())
        val scopeValue = formatScope(header)
        cursor.y = PdfChrome.drawLabeledLines(
            cursor.canvas,
            listOf(
                context.getString(R.string.patient_report_pdf_scope_label) to scopeValue,
                context.getString(R.string.patient_report_pdf_generated_by_label) to header.generatedBy,
                context.getString(R.string.patient_report_pdf_generated_at_label) to
                    dateFormatter.format(header.generatedAt),
            ),
            cursor.y,
        )
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

    private fun drawSummaryBlock(cursor: PageCursor, header: PatientReportPdfHeader) {
        cursor.y = PdfChrome.drawSectionHeader(
            cursor.canvas,
            context.getString(R.string.patient_report_pdf_summary_title),
            cursor.y,
        )
        val positiveSpeciesValue = header.positiveSpecies.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: context.getString(R.string.report_no_positive_species)
        cursor.y = PdfChrome.drawLabeledLines(
            cursor.canvas,
            listOf(
                context.getString(R.string.patient_report_pdf_total_sessions_label) to header.totalSessions.toString(),
                context.getString(R.string.patient_report_pdf_total_samples_label) to header.totalSamples.toString(),
                context.getString(R.string.patient_report_pdf_total_eggs_label) to header.totalEggsConfirmed.toString(),
                context.getString(R.string.patient_report_pdf_positive_species_label) to positiveSpeciesValue,
            ),
            cursor.y,
        )
    }

    /** Pooled findings across every included session — the headline table (D3). */
    private fun drawPooledSpeciesTable(cursor: PageCursor, rows: List<ReportPdfSpeciesRow>, totalSessions: Int) {
        cursor.y = PdfChrome.drawSectionHeader(
            cursor.canvas,
            context.getString(R.string.patient_report_pdf_species_table_title),
            cursor.y,
        )

        if (rows.isEmpty()) {
            val message = context.resources.getQuantityString(
                R.plurals.patient_report_pdf_no_parasites_sessions,
                totalSessions,
                totalSessions,
            )
            cursor.canvas.drawText(message, PdfChrome.MARGIN, cursor.y, PdfChrome.paintFor(PdfChrome.TextStyle.VALUE))
            cursor.y += PdfChrome.LINE_HEIGHT
            return
        }

        val headerPaint = PdfChrome.paintFor(PdfChrome.TextStyle.TABLE_HEADER)
        val speciesLabel = context.getString(R.string.report_pdf_column_species)
        val lpfLabel = context.getString(R.string.report_pdf_column_lpf)
        val readingLabel = context.getString(R.string.report_pdf_column_reading)
        cursor.canvas.drawText(speciesLabel, PdfChrome.MARGIN, cursor.y, headerPaint)
        cursor.canvas.drawText(lpfLabel, SPECIES_LPF_COLUMN_X, cursor.y, headerPaint)
        cursor.canvas.drawText(readingLabel, SPECIES_READING_COLUMN_X, cursor.y, headerPaint)
        cursor.y += PdfChrome.LINE_HEIGHT

        val rowPaint = PdfChrome.paintFor(PdfChrome.TextStyle.VALUE)
        rows.forEach { row ->
            cursor.canvas.drawText(row.speciesDisplayName, PdfChrome.MARGIN, cursor.y, rowPaint)
            cursor.canvas.drawText("%d–%d".format(row.min, row.max), SPECIES_LPF_COLUMN_X, cursor.y, rowPaint)
            row.descriptor?.let {
                cursor.canvas.drawText(
                    context.getString(it.labelRes),
                    SPECIES_READING_COLUMN_X,
                    cursor.y,
                    rowPaint,
                )
            }
            cursor.y += PdfChrome.LINE_HEIGHT
        }
        cursor.y += UNIT_NOTE_GAP
        cursor.canvas.drawText(
            context.getString(R.string.report_pdf_lpf_unit_note),
            PdfChrome.MARGIN,
            cursor.y,
            PdfChrome.paintFor(PdfChrome.TextStyle.NOTE),
        )
        cursor.y += PdfChrome.LINE_HEIGHT
    }

    /** The per-session breakdown table (D3): one row per included smear, paginated. */
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

    /**
     * Owns the current [PdfDocument.Page]/[Canvas]/cursor-y and turns the page when content
     * would not fit above the footer, drawing the finished page's footer before starting the
     * next one.
     */
    private inner class PageCursor(private val pdfDocument: PdfDocument) {
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
        fun finishPage() {
            drawFooter(canvas, pageNumber)
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
            finishPage()
            startPage()
            return true
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerText = context.getString(R.string.patient_report_pdf_footer, pageNumber)
        canvas.drawText(
            footerText,
            PdfChrome.MARGIN,
            PdfChrome.PAGE_HEIGHT - PdfChrome.MARGIN / 2f,
            PdfChrome.paintFor(PdfChrome.TextStyle.NOTE),
        )
    }

    private companion object {
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
        private const val SCOPE_DATE_PATTERN = "yyyy-MM-dd"
        private const val SESSION_ID_TAKE = 8
        private const val SEMICOLON_SEPARATOR = "; "

        private const val SPECIES_LPF_COLUMN_X = 300f
        private const val SPECIES_READING_COLUMN_X = 400f
        private const val UNIT_NOTE_GAP = 10f

        private const val SESSION_COLUMN_X = 110f
        private const val SAMPLES_COLUMN_X = 230f
        private const val EGGS_COLUMN_X = 290f
        private const val FINDINGS_COLUMN_X = 340f

        private const val FOOTER_RESERVE = 24f
    }
}
