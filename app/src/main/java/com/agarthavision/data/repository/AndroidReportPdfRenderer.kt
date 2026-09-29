package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.agarthavision.R
import com.agarthavision.domain.model.LpfDescriptor
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.ui.records.labelRes
import com.agarthavision.ui.theme.AppColors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders [ReportPdfDocument] to a single-page PDF using `android.graphics.pdf.PdfDocument`.
 *
 * All static chrome — title, section headers, column headers, unit labels — is resolved from
 * `strings.xml` (per C11); only [ReportPdfDocument]'s dynamic fields are interpolated. Colors
 * come from [AppColors], the app's one palette (C11: no raw hex outside palette files), via
 * [PdfTextStyle].
 */
// The patient block split the old header block in two and added a sex/age helper; each function
// draws one self-contained piece of the page, and splitting further would just move the same
// lines around without reducing the real complexity.
@Suppress("TooManyFunctions")
class AndroidReportPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportPdfRenderer {

    override suspend fun render(document: ReportPdfDocument): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, PAGE_NUMBER).create()
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
        val titleX = drawLogo(canvas)
        val titlePaint = paintFor(PdfTextStyle.TITLE)
        // Center the wordmark on the logo's vertical midline via font metrics, so the two read
        // as one letterhead band rather than the title floating off the logo's baseline.
        val metrics = titlePaint.fontMetrics
        val titleBaseline = MARGIN + LOGO_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            context.getString(R.string.report_pdf_document_title),
            titleX,
            titleBaseline,
            titlePaint,
        )
        var cursorY = MARGIN + LOGO_SIZE + SECTION_GAP
        cursorY = drawPatientBlock(canvas, document.header, cursorY)
        cursorY += SECTION_GAP
        cursorY = drawSessionBlock(canvas, document.header, cursorY)
        cursorY += SECTION_GAP
        cursorY = drawSummaryBlock(canvas, document.header, cursorY)
        cursorY += SECTION_GAP
        drawSpeciesTable(canvas, document.speciesRows, document.header.fieldsExamined, cursorY)
        drawFooter(canvas)
    }

    private fun drawPatientBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        val y = drawSectionHeader(canvas, context.getString(R.string.report_pdf_patient_section_title), startY)
        val ageValue = context.getString(R.string.report_pdf_age_value, header.patientAgeYears)
        return drawLabeledLines(
            canvas,
            listOf(
                context.getString(R.string.report_pdf_patient_name_label) to header.patientName,
                context.getString(R.string.report_pdf_sex_label) to context.getString(sexLabelRes(header.patientSex)),
                context.getString(R.string.report_pdf_age_label) to ageValue,
                context.getString(R.string.report_pdf_barangay_label) to header.barangayLabel,
            ),
            y,
        )
    }

    private fun drawSessionBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(ZoneId.systemDefault())
        return drawLabeledLines(
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
        val y = drawSectionHeader(canvas, context.getString(R.string.report_pdf_summary_title), startY)
        val positiveSpeciesValue = header.positiveSpecies.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: context.getString(R.string.report_no_positive_species)
        return drawLabeledLines(
            canvas,
            listOf(
                context.getString(R.string.report_pdf_total_eggs_label) to header.totalEggsConfirmed.toString(),
                context.getString(R.string.report_pdf_positive_species_label) to positiveSpeciesValue,
            ),
            y,
        )
    }

    private fun sexLabelRes(sex: Sex?): Int = when (sex) {
        Sex.MALE -> R.string.patients_sex_male
        Sex.FEMALE -> R.string.patients_sex_female
        null -> R.string.patients_sex_unknown
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
        var y = drawSectionHeader(canvas, context.getString(R.string.report_pdf_species_table_title), startY)

        if (rows.isEmpty()) {
            val message = context.resources.getQuantityString(
                R.plurals.report_pdf_no_parasites_fields,
                fieldsExamined,
                fieldsExamined,
            )
            canvas.drawText(message, MARGIN, y, paintFor(PdfTextStyle.VALUE))
            return
        }

        val headerPaint = paintFor(PdfTextStyle.TABLE_HEADER)
        canvas.drawText(context.getString(R.string.report_pdf_column_species), MARGIN, y, headerPaint)
        canvas.drawText(context.getString(R.string.report_pdf_column_lpf), LPF_COLUMN_X, y, headerPaint)
        canvas.drawText(context.getString(R.string.report_pdf_column_reading), READING_COLUMN_X, y, headerPaint)
        y += LINE_HEIGHT

        val rowPaint = paintFor(PdfTextStyle.VALUE)
        rows.forEach { row ->
            canvas.drawText(row.speciesDisplayName, MARGIN, y, rowPaint)
            canvas.drawText("%d–%d".format(row.min, row.max), LPF_COLUMN_X, y, rowPaint)
            // Blank rather than a dash when a species was never seen: the row would not be on
            // the page at all in that case, and inventing a reading for one is worse than none.
            row.descriptor?.let { canvas.drawText(context.getString(it.labelRes), READING_COLUMN_X, y, rowPaint) }
            y += LINE_HEIGHT
        }

        y += UNIT_NOTE_GAP
        canvas.drawText(context.getString(R.string.report_pdf_lpf_unit_note), MARGIN, y, paintFor(PdfTextStyle.NOTE))
    }

    /**
     * Draws the AgarthaVision brand mark at the left margin, vertically centered against the
     * title's baseline band, and returns the X the title should start at. The vector keeps its
     * own colors — it's a brand drawable, not something to tint — so a missing drawable is the
     * only failure mode, and it's handled by simply leaving the title at [MARGIN].
     */
    private fun drawLogo(canvas: Canvas): Float {
        val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo) ?: return MARGIN
        // Rasterize the vector supersampled, then draw it down into the 36pt box. Drawing a
        // VectorDrawable straight onto the page canvas bakes it at ~72 dpi and looks rough; a
        // LOGO_SUPERSAMPLE× bitmap embeds a crisp mark that holds up zoomed and printed.
        val sizePx = (LOGO_SIZE * LOGO_SUPERSAMPLE).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        logo.setBounds(0, 0, sizePx, sizePx)
        logo.draw(Canvas(bitmap))
        val destination = RectF(MARGIN, MARGIN, MARGIN + LOGO_SIZE, MARGIN + LOGO_SIZE)
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(bitmap, null, destination, logoPaint)
        bitmap.recycle()
        return MARGIN + LOGO_SIZE + LOGO_TITLE_GAP
    }

    private fun drawFooter(canvas: Canvas) {
        val footerText = context.getString(R.string.report_pdf_footer, PAGE_NUMBER)
        canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - MARGIN / 2f, paintFor(PdfTextStyle.NOTE))
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, y: Float): Float {
        canvas.drawText(title, MARGIN, y, paintFor(PdfTextStyle.SECTION_HEADER))
        return y + LINE_HEIGHT
    }

    /** Draws each `label: value` pair below [startY], one per line, and returns the next y. */
    private fun drawLabeledLines(canvas: Canvas, lines: List<Pair<String, String>>, startY: Float): Float {
        var y = startY
        lines.forEach { (label, value) -> y = drawLabeledLine(canvas, label, value, y) }
        return y
    }

    private fun drawLabeledLine(canvas: Canvas, label: String, value: String, y: Float): Float {
        canvas.drawText("$label:", MARGIN, y, paintFor(PdfTextStyle.LABEL))
        val valuePaint = paintFor(PdfTextStyle.VALUE)
        val maxValueWidth = PAGE_WIDTH - MARGIN - LABEL_COLUMN_WIDTH
        canvas.drawText(truncate(valuePaint, value, maxValueWidth), LABEL_COLUMN_WIDTH, y, valuePaint)
        return y + LINE_HEIGHT
    }

    /** Truncates [text] with a trailing ellipsis when it would overflow [maxWidth] under [paint]. */
    private fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val availableWidth = maxWidth - paint.measureText(ELLIPSIS)
        val fittingChars = paint.breakText(text, true, availableWidth, null)
        return text.substring(0, fittingChars) + ELLIPSIS
    }

    private fun paintFor(style: PdfTextStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.color.toArgb()
        textSize = style.textSize
        isFakeBoldText = style.bold
    }

    /**
     * The renderer's small, fixed set of text styles. All colors come from [AppColors] (C11: no
     * raw hex outside palette files); consolidated here (rather than one Paint-builder function
     * per style) to keep this class's function count under detekt's TooManyFunctions threshold.
     */
    private enum class PdfTextStyle(val color: Color, val textSize: Float, val bold: Boolean) {
        TITLE(AppColors.Maroon, TITLE_TEXT_SIZE, bold = true),
        SECTION_HEADER(AppColors.Gray900, SECTION_TEXT_SIZE, bold = true),
        TABLE_HEADER(AppColors.Gray700, BODY_TEXT_SIZE, bold = true),
        LABEL(AppColors.Gray500, BODY_TEXT_SIZE, bold = false),
        VALUE(AppColors.Gray900, BODY_TEXT_SIZE, bold = false),
        NOTE(AppColors.Gray400, NOTE_TEXT_SIZE, bold = false),
    }

    private companion object {
        // A4 at 72 dpi (points), matching android.graphics.pdf.PdfDocument's unit.
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val PAGE_NUMBER = 1

        private const val MARGIN = 40f
        private const val LABEL_COLUMN_WIDTH = 160f
        private const val LPF_COLUMN_X = 300f

        // Right of the range, with room for "numerous" inside the page's right margin.
        private const val READING_COLUMN_X = 400f
        private const val LINE_HEIGHT = 22f
        private const val SECTION_GAP = 16f
        private const val UNIT_NOTE_GAP = 10f

        private const val TITLE_TEXT_SIZE = 18f
        private const val SECTION_TEXT_SIZE = 14f
        private const val BODY_TEXT_SIZE = 12f
        private const val NOTE_TEXT_SIZE = 10f
        private const val LOGO_SIZE = 36f
        private const val LOGO_TITLE_GAP = 10f
        private const val LOGO_SUPERSAMPLE = 8

        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
        private const val ELLIPSIS = "…"
    }
}
