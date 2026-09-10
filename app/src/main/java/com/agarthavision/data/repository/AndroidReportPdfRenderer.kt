package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.agarthavision.R
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.repository.ReportPdfRenderer
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
        canvas.drawText(
            context.getString(R.string.report_pdf_document_title),
            titleX,
            MARGIN + TITLE_BASELINE_OFFSET,
            paintFor(PdfTextStyle.TITLE),
        )
        var cursorY = MARGIN + maxOf(LOGO_SIZE, TITLE_HEIGHT)
        cursorY = drawHeaderBlock(canvas, document.header, cursorY)
        cursorY += SECTION_GAP
        cursorY = drawSummaryBlock(canvas, document.header, cursorY)
        cursorY += SECTION_GAP
        drawSpeciesTable(canvas, document.speciesRows, cursorY)
        drawFooter(canvas)
    }

    private fun drawHeaderBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        var y = startY
        val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(ZoneId.systemDefault())

        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_session_label),
            header.sessionLabel ?: header.sessionId,
            y,
        )
        y = drawLabeledLine(canvas, context.getString(R.string.report_pdf_device_label), header.deviceId, y)
        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_generated_by_label),
            header.generatedBy,
            y,
        )
        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_generated_at_label),
            dateFormatter.format(header.generatedAt),
            y,
        )
        return y
    }

    private fun drawSummaryBlock(canvas: Canvas, header: ReportPdfHeader, startY: Float): Float {
        var y = drawSectionHeader(canvas, context.getString(R.string.report_pdf_summary_title), startY)

        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_total_samples_label),
            header.totalSamples.toString(),
            y,
        )
        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_total_eggs_label),
            header.totalEggsConfirmed.toString(),
            y,
        )
        val positiveSpeciesValue = header.positiveSpecies.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: context.getString(R.string.report_no_positive_species)
        y = drawLabeledLine(
            canvas,
            context.getString(R.string.report_pdf_positive_species_label),
            positiveSpeciesValue,
            y,
        )
        return y
    }

    private fun drawSpeciesTable(canvas: Canvas, rows: List<ReportPdfSpeciesRow>, startY: Float) {
        var y = drawSectionHeader(canvas, context.getString(R.string.report_pdf_species_table_title), startY)

        val headerPaint = paintFor(PdfTextStyle.TABLE_HEADER)
        canvas.drawText(context.getString(R.string.report_pdf_column_species), MARGIN, y, headerPaint)
        canvas.drawText(context.getString(R.string.report_pdf_column_epg), EPG_COLUMN_X, y, headerPaint)
        y += LINE_HEIGHT

        val rowPaint = paintFor(PdfTextStyle.VALUE)
        rows.forEach { row ->
            canvas.drawText(row.speciesDisplayName, MARGIN, y, rowPaint)
            canvas.drawText(row.epg.toString(), EPG_COLUMN_X, y, rowPaint)
            y += LINE_HEIGHT
        }

        y += UNIT_NOTE_GAP
        canvas.drawText(context.getString(R.string.report_pdf_epg_unit_note), MARGIN, y, paintFor(PdfTextStyle.NOTE))
    }

    /**
     * Draws the AgarthaVision brand mark at the left margin, vertically centered against the
     * title's baseline band, and returns the X the title should start at. The vector keeps its
     * own colors — it's a brand drawable, not something to tint — so a missing drawable is the
     * only failure mode, and it's handled by simply leaving the title at [MARGIN].
     */
    private fun drawLogo(canvas: Canvas): Float {
        val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo) ?: return MARGIN
        val top = (MARGIN + TITLE_BASELINE_OFFSET - LOGO_SIZE / 2f).toInt()
        logo.setBounds(MARGIN.toInt(), top, (MARGIN + LOGO_SIZE).toInt(), top + LOGO_SIZE.toInt())
        logo.draw(canvas)
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

    private fun drawLabeledLine(canvas: Canvas, label: String, value: String, y: Float): Float {
        canvas.drawText("$label:", MARGIN, y, paintFor(PdfTextStyle.LABEL))
        canvas.drawText(value, LABEL_COLUMN_WIDTH, y, paintFor(PdfTextStyle.VALUE))
        return y + LINE_HEIGHT
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
        private const val EPG_COLUMN_X = 300f
        private const val LINE_HEIGHT = 22f
        private const val SECTION_GAP = 16f
        private const val UNIT_NOTE_GAP = 10f

        private const val TITLE_TEXT_SIZE = 18f
        private const val SECTION_TEXT_SIZE = 14f
        private const val BODY_TEXT_SIZE = 12f
        private const val NOTE_TEXT_SIZE = 10f
        private const val TITLE_HEIGHT = 30f
        private const val TITLE_BASELINE_OFFSET = 6f
        private const val LOGO_SIZE = 36f
        private const val LOGO_TITLE_GAP = 10f

        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    }
}
