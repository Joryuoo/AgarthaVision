package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.agarthavision.R
import com.agarthavision.domain.model.Sex
import com.agarthavision.ui.theme.AppColors

/**
 * Shared letterhead, typography, and low-level drawing primitives for the two
 * `android.graphics.pdf.PdfDocument` renderers ([AndroidReportPdfRenderer] session-scoped,
 * [AndroidPatientReportPdfRenderer] patient-scoped, 14zcqntj2uz).
 *
 * Extracted rather than duplicated a second time: both documents share the same brand mark,
 * page geometry, and `label: value` line layout, and a renderer-specific tweak to one that
 * quietly skipped the other is exactly the kind of drift C11 (one design system) exists to
 * prevent. Colors come from [AppColors] (C11: no raw hex outside palette files).
 */
internal object PdfChrome {
    // A4 at 72 dpi (points), matching android.graphics.pdf.PdfDocument's unit.
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    const val MARGIN = 40f
    const val LABEL_COLUMN_WIDTH = 160f
    const val LINE_HEIGHT = 22f
    const val SECTION_GAP = 16f

    const val TITLE_TEXT_SIZE = 18f
    const val SECTION_TEXT_SIZE = 14f
    const val BODY_TEXT_SIZE = 12f
    const val NOTE_TEXT_SIZE = 10f
    const val LOGO_SIZE = 36f
    const val LOGO_TITLE_GAP = 10f
    private const val LOGO_SUPERSAMPLE = 8

    const val ELLIPSIS = "…"

    /**
     * The renderers' small, fixed set of text styles. All colors come from [AppColors].
     */
    enum class TextStyle(val color: Color, val textSize: Float, val bold: Boolean) {
        TITLE(AppColors.Maroon, TITLE_TEXT_SIZE, bold = true),
        SECTION_HEADER(AppColors.Gray900, SECTION_TEXT_SIZE, bold = true),
        TABLE_HEADER(AppColors.Gray700, BODY_TEXT_SIZE, bold = true),
        LABEL(AppColors.Gray500, BODY_TEXT_SIZE, bold = false),
        VALUE(AppColors.Gray900, BODY_TEXT_SIZE, bold = false),
        NOTE(AppColors.Gray400, NOTE_TEXT_SIZE, bold = false),
    }

    fun paintFor(style: TextStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.color.toArgb()
        textSize = style.textSize
        isFakeBoldText = style.bold
    }

    fun sexLabelRes(sex: Sex?): Int = when (sex) {
        Sex.MALE -> R.string.patients_sex_male
        Sex.FEMALE -> R.string.patients_sex_female
        null -> R.string.patients_sex_unknown
    }

    /**
     * Draws the AgarthaVision brand mark at the left margin, vertically centered against the
     * title's baseline band, and returns the X the title should start at. The vector keeps its
     * own colors — it's a brand drawable, not something to tint — so a missing drawable is the
     * only failure mode, and it's handled by simply leaving the title at [MARGIN].
     */
    fun drawLogo(context: Context, canvas: Canvas): Float {
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

    fun drawSectionHeader(canvas: Canvas, title: String, y: Float): Float {
        canvas.drawText(title, MARGIN, y, paintFor(TextStyle.SECTION_HEADER))
        return y + LINE_HEIGHT
    }

    /** Draws each `label: value` pair below [startY], one per line, and returns the next y. */
    fun drawLabeledLines(canvas: Canvas, lines: List<Pair<String, String>>, startY: Float): Float {
        var y = startY
        lines.forEach { (label, value) -> y = drawLabeledLine(canvas, label, value, y) }
        return y
    }

    fun drawLabeledLine(canvas: Canvas, label: String, value: String, y: Float): Float {
        canvas.drawText("$label:", MARGIN, y, paintFor(TextStyle.LABEL))
        val valuePaint = paintFor(TextStyle.VALUE)
        val maxValueWidth = PAGE_WIDTH - MARGIN - LABEL_COLUMN_WIDTH
        canvas.drawText(truncate(valuePaint, value, maxValueWidth), LABEL_COLUMN_WIDTH, y, valuePaint)
        return y + LINE_HEIGHT
    }

    /** Truncates [text] with a trailing ellipsis when it would overflow [maxWidth] under [paint]. */
    fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val availableWidth = maxWidth - paint.measureText(ELLIPSIS)
        val fittingChars = paint.breakText(text, true, availableWidth, null)
        return text.substring(0, fittingChars) + ELLIPSIS
    }
}
