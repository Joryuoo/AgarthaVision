package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.agarthavision.R
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.ui.records.labelRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders [ReportPdfDocument] to a single-page PDF using `android.graphics.pdf.PdfDocument`.
 *
 * All static chrome — title, section headers, column headers, unit labels — is resolved from
 * `strings.xml` (per C11); only [ReportPdfDocument]'s dynamic fields are interpolated. Shared
 * letterhead, typography and `label: value` drawing live in [PdfChrome], alongside
 * [AndroidPatientReportPdfRenderer].
 */
class AndroidReportPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportPdfRenderer {

    override suspend fun render(document: ReportPdfDocument): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PdfChrome.PAGE_WIDTH, PdfChrome.PAGE_HEIGHT, PAGE_NUMBER).create()
        val page = pdfDocument.startPage(pageInfo)
        drawPage(page.canvas, document)
        pdfDocument.finishPage(page)

        return ByteArrayOutputStream().use { output ->
            pdfDocument.writeTo(output)
            pdfDocument.close()
            output.toByteArray()
        }
    }

    private fun drawPage(canvas: Canvas, document: ReportPdfDocument) {
        val titleX = PdfChrome.drawLogo(context, canvas)
        val titlePaint = PdfChrome.paintFor(PdfChrome.TextStyle.TITLE)
        // Center the wordmark on the logo's vertical midline via font metrics, so the two read
        // as one letterhead band rather than the title floating off the logo's baseline.
        val metrics = titlePaint.fontMetrics
        val titleBaseline = PdfChrome.MARGIN + PdfChrome.LOGO_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            context.getString(R.string.report_pdf_document_title),
            titleX,
            titleBaseline,
            titlePaint,
        )
        var cursorY = PdfChrome.MARGIN + PdfChrome.LOGO_SIZE + PdfChrome.SECTION_GAP
        cursorY = drawPatientBlock(canvas, document.header, cursorY)
        cursorY += PdfChrome.SECTION_GAP
        cursorY = drawSessionBlock(canvas, document.header, cursorY)
        cursorY += PdfChrome.SECTION_GAP
        cursorY = drawSummaryBlock(canvas, document.header, cursorY)
        cursorY += PdfChrome.SECTION_GAP
        drawSpeciesTable(canvas, document.speciesRows, document.header.fieldsExamined, cursorY)
        drawFooter(canvas)
    }

    private fun drawPatientBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        val patientTitle = context.getString(R.string.report_pdf_patient_section_title)
        val y = PdfChrome.drawSectionHeader(canvas, patientTitle, startY)
        val ageValue = context.getString(R.string.report_pdf_age_value, header.patientAgeYears)
        return PdfChrome.drawLabeledLines(
            canvas,
            listOf(
                context.getString(R.string.report_pdf_patient_name_label) to header.patientName,
                context.getString(R.string.report_pdf_sex_label) to
                    context.getString(PdfChrome.sexLabelRes(header.patientSex)),
                context.getString(R.string.report_pdf_age_label) to ageValue,
                context.getString(R.string.report_pdf_barangay_label) to header.barangayLabel,
            ),
            y,
        )
    }

    private fun drawSessionBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(ZoneId.systemDefault())
        return PdfChrome.drawLabeledLines(
            canvas,
            listOf(
                context.getString(R.string.report_pdf_session_label) to (header.sessionLabel ?: header.sessionId),
                context.getString(R.string.report_pdf_fields_examined_label) to header.fieldsExamined.toString(),
                context.getString(R.string.report_pdf_generated_by_label) to header.generatedByName,
                context.getString(R.string.report_pdf_generated_at_label) to dateFormatter.format(header.generatedAt),
            ),
            startY,
        )
    }

    private fun drawSummaryBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        val y = PdfChrome.drawSectionHeader(canvas, context.getString(R.string.report_pdf_summary_title), startY)
        val positiveSpeciesValue = header.positiveSpecies.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: context.getString(R.string.report_no_positive_species)
        return PdfChrome.drawLabeledLines(
            canvas,
            listOf(
                context.getString(R.string.report_pdf_total_eggs_label) to header.totalEggsConfirmed.toString(),
                context.getString(R.string.report_pdf_positive_species_label) to positiveSpeciesValue,
            ),
            y,
        )
    }

    /**
     * The per-species table: one row per species found, its LPF range, and its reading.
     *
     * **A session with nothing in it prints a sentence, not an empty table.** A wholly negative
     * smear is a real result and the most common one in surveillance - it is the outcome a
     * report is most often needed for - and a blank table under a heading asks the person
     * holding the page to work out whether the app found nothing or failed to look. Session
     * Detail says the same thing in the same words, which is the consistency PB-18 asks for.
     */
    private fun drawSpeciesTable(
        canvas: Canvas,
        rows: List<ReportPdfSpeciesRow>,
        fieldsExamined: Int,
        startY: Float,
    ) {
        var y = PdfChrome.drawSectionHeader(canvas, context.getString(R.string.report_pdf_species_table_title), startY)

        if (rows.isEmpty()) {
            val message = context.resources.getQuantityString(
                R.plurals.report_pdf_no_parasites_fields,
                fieldsExamined,
                fieldsExamined,
            )
            canvas.drawText(message, PdfChrome.MARGIN, y, PdfChrome.paintFor(PdfChrome.TextStyle.VALUE))
            return
        }

        val headerPaint = PdfChrome.paintFor(PdfChrome.TextStyle.TABLE_HEADER)
        canvas.drawText(context.getString(R.string.report_pdf_column_species), PdfChrome.MARGIN, y, headerPaint)
        canvas.drawText(context.getString(R.string.report_pdf_column_lpf), LPF_COLUMN_X, y, headerPaint)
        canvas.drawText(context.getString(R.string.report_pdf_column_reading), READING_COLUMN_X, y, headerPaint)
        y += PdfChrome.LINE_HEIGHT

        val rowPaint = PdfChrome.paintFor(PdfChrome.TextStyle.VALUE)
        rows.forEach { row ->
            canvas.drawText(row.speciesDisplayName, PdfChrome.MARGIN, y, rowPaint)
            canvas.drawText("%d–%d".format(row.min, row.max), LPF_COLUMN_X, y, rowPaint)
            // Blank rather than a dash when a species was never seen: the row would not be on
            // the page at all in that case, and inventing a reading for one is worse than none.
            row.descriptor?.let { canvas.drawText(context.getString(it.labelRes), READING_COLUMN_X, y, rowPaint) }
            y += PdfChrome.LINE_HEIGHT
        }

        y += UNIT_NOTE_GAP
        canvas.drawText(
            context.getString(R.string.report_pdf_lpf_unit_note),
            PdfChrome.MARGIN,
            y,
            PdfChrome.paintFor(PdfChrome.TextStyle.NOTE),
        )
    }

    private fun drawFooter(canvas: Canvas) {
        val footerText = context.getString(R.string.report_pdf_footer, PAGE_NUMBER)
        canvas.drawText(
            footerText,
            PdfChrome.MARGIN,
            PdfChrome.PAGE_HEIGHT - PdfChrome.MARGIN / 2f,
            PdfChrome.paintFor(PdfChrome.TextStyle.NOTE),
        )
    }

    private companion object {
        private const val PAGE_NUMBER = 1
        private const val LPF_COLUMN_X = 300f

        // Right of the range, with room for "numerous" inside the page's right margin.
        private const val READING_COLUMN_X = 400f
        private const val UNIT_NOTE_GAP = 10f

        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    }
}
