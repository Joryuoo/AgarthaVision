package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.ReportPdfSampleItem
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sex
import com.agarthavision.ui.records.labelRes
import com.agarthavision.ui.theme.AppColors
import java.io.File

/**
 * Shared letterhead, typography, and low-level drawing primitives for the two
 * `android.graphics.pdf.PdfDocument` renderers ([AndroidReportPdfRenderer] session-scoped,
 * [AndroidPatientReportPdfRenderer] patient-scoped).
 *
 * All chrome colors are sourced from [AppColors] (C11: no raw hex outside palette files).
 */
@Suppress("TooManyFunctions", "LargeClass", "MagicNumber")
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

    const val SAMPLES_PER_APPENDIX_PAGE = 4
    private const val TARGET_IMAGE_PX = 512
    private const val SHORT_ID_LENGTH = 8
    private const val LETTERHEAD_LOGO_SIZE = 36f
    private const val RULE_STROKE_WIDTH = 1f

    private const val CORNER_RADIUS_CARD = 10f
    private const val CORNER_RADIUS_HERO = 14f
    private const val CORNER_RADIUS_PILL = 4f
    private const val CORNER_RADIUS_IMAGE = 6f
    private const val SIGNATURE_LINE_WIDTH = 220f

    private const val CARD_PADDING = 16f
    private const val CARD_BOTTOM_SPACING = 24f
    private const val META_CARD_ROW_HEIGHT = 36f
    private const val META_CARD_1ROW_HEIGHT = 44f
    private const val HERO_LABEL_TO_CARD_GAP = 10f
    private const val HERO_PADDING_X = 16f
    private const val HERO_PADDING_TOP = 14f
    private const val HERO_PADDING_BOTTOM = 14f
    private const val HERO_ROW_HEIGHT = 38f
    private const val HERO_HEADER_HEIGHT = 111f
    private const val HERO_EMPTY_HEIGHT = 22f

    private const val APPENDIX_CARD_PADDING = 10f
    private const val APPENDIX_GRID_GAP = 10f
    private const val APPENDIX_CARD_WIDTH = 251f
    private const val APPENDIX_CARD_HEIGHT = 280f

    private const val FOOTER_RULE_BOTTOM_OFFSET = 38f
    private const val FOOTER_TEXT_BOTTOM_OFFSET = 24f

    private const val WHITE_ALPHA_HERO_LABEL = 0.8f
    private const val WHITE_ALPHA_HERO_DIVIDER = 0.2f
    private const val WHITE_ALPHA_HERO_PILL = 0.18f
    private const val WHITE_ALPHA_HERO_SUBTEXT = 0.85f
    private const val WHITE_ALPHA_HERO_EMPTY = 0.9f

    private const val TEXT_SIZE_BRAND = 15f
    private const val TEXT_SIZE_LETTERHEAD_SUB = 9.5f
    private const val TEXT_SIZE_MAIN_TITLE = 24f
    private const val TEXT_SIZE_TITLE_SUB = 11f
    private const val TEXT_SIZE_META_LABEL = 8.5f
    private const val TEXT_SIZE_META_VALUE = 11f
    private const val TEXT_SIZE_HERO_LABEL = 9.5f
    private const val TEXT_SIZE_HERO_EGGS = 36f
    private const val TEXT_SIZE_HERO_META = 11f
    private const val TEXT_SIZE_HERO_SPECIES = 12f
    private const val TEXT_SIZE_HERO_PILL = 8f
    private const val TEXT_SIZE_HERO_LPF = 16f
    private const val TEXT_SIZE_HERO_LPF_UNIT = 9f
    private const val TEXT_SIZE_HERO_EMPTY = 10.5f
    private const val TEXT_SIZE_SIGNOFF_HEADER = 9.5f
    private const val TEXT_SIZE_SIGNOFF_NAME = 13.5f
    private const val TEXT_SIZE_SIGNOFF_SUB = 10.5f
    private const val TEXT_SIZE_FOOTER = 8.5f
    private const val TEXT_SIZE_APPENDIX_FINDING = 10.5f
    private const val TEXT_SIZE_APPENDIX_FIELD = 8f
    private const val TEXT_SIZE_APPENDIX_EGGS = 8.5f
    private const val TEXT_SIZE_PLACEHOLDER_TITLE = 8.5f
    private const val TEXT_SIZE_PLACEHOLDER_SUB = 7.5f

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

    fun pdfPaint(
        color: Color,
        textSize: Float,
        weight: Int = 400,
        italic: Boolean = false,
        letterSpacing: Float = 0f,
    ): Paint {
        val flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
        return Paint(flags).apply {
            this.color = color.toArgb()
            this.textSize = textSize
            this.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.SANS_SERIF, weight, italic)
            } else {
                val family = if (weight >= 600) "sans-serif-medium" else "sans-serif"
                val style = when {
                    italic && weight >= 700 -> Typeface.BOLD_ITALIC
                    italic -> Typeface.ITALIC
                    weight >= 700 -> Typeface.BOLD
                    else -> Typeface.NORMAL
                }
                Typeface.create(family, style)
            }
            this.fontFeatureSettings = "tnum, cv11, ss01, ss03"
            this.letterSpacing = letterSpacing
        }
    }

    fun paintFor(style: TextStyle): Paint = pdfPaint(
        color = style.color,
        textSize = style.textSize,
        weight = if (style.bold) 700 else 400,
        letterSpacing = 0.01f,
    )

    fun sexLabelRes(sex: Sex?): Int = when (sex) {
        Sex.MALE -> R.string.patients_sex_male
        Sex.FEMALE -> R.string.patients_sex_female
        null -> R.string.patients_sex_unknown
    }

    fun drawLogo(context: Context, canvas: Canvas): Float {
        val destination = RectF(MARGIN, MARGIN, MARGIN + LOGO_SIZE, MARGIN + LOGO_SIZE)
        drawLogo(context, canvas, destination)
        return MARGIN + LOGO_SIZE + LOGO_TITLE_GAP
    }

    fun drawLogo(context: Context, canvas: Canvas, destination: RectF) {
        val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo) ?: return
        val sizePx = (destination.width() * LOGO_SUPERSAMPLE).toInt()
        if (sizePx <= 0) return
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        logo.setBounds(0, 0, sizePx, sizePx)
        logo.draw(Canvas(bitmap))
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(bitmap, null, destination, logoPaint)
        bitmap.recycle()
    }

    fun drawLetterhead(
        context: Context,
        canvas: Canvas,
        organizationName: String?,
        startY: Float = MARGIN,
    ): Float {
        val logoDest = RectF(MARGIN, startY, MARGIN + LETTERHEAD_LOGO_SIZE, startY + LETTERHEAD_LOGO_SIZE)
        drawLogo(context, canvas, logoDest)

        val textStartX = MARGIN + LETTERHEAD_LOGO_SIZE + 12f
        val brandPaint = pdfPaint(
            color = AppColors.Maroon,
            textSize = TEXT_SIZE_BRAND,
            weight = 700,
            letterSpacing = 0.06f,
        )
        canvas.drawText(context.getString(R.string.report_pdf_brand_title), textStartX, startY + 15f, brandPaint)

        val subtitle = if (!organizationName.isNullOrBlank()) {
            context.getString(R.string.report_pdf_letterhead_subtitle_with_org, organizationName.uppercase())
        } else {
            context.getString(R.string.report_pdf_letterhead_subtitle_default)
        }
        val subPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_LETTERHEAD_SUB,
            weight = 500,
            letterSpacing = 0.05f,
        )
        canvas.drawText(subtitle, textStartX, startY + 29f, subPaint)

        val ruleY = startY + LETTERHEAD_LOGO_SIZE + 10f
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            strokeWidth = RULE_STROKE_WIDTH
        }
        canvas.drawLine(MARGIN, ruleY, PAGE_WIDTH - MARGIN, ruleY, rulePaint)
        return ruleY + 20f
    }

    fun drawReportTitle(canvas: Canvas, title: String, subtitle: String, startY: Float): Float {
        val titlePaint = pdfPaint(
            color = AppColors.Maroon,
            textSize = TEXT_SIZE_MAIN_TITLE,
            weight = 700,
            letterSpacing = 0.01f,
        )
        val titleBaseline = startY + 20f
        canvas.drawText(title, MARGIN, titleBaseline, titlePaint)

        val subPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_TITLE_SUB,
            weight = 400,
            letterSpacing = 0.01f,
        )
        val subBaseline = titleBaseline + 18f
        canvas.drawText(subtitle, MARGIN, subBaseline, subPaint)
        return subBaseline + 18f
    }

    fun drawMetadataCard(
        canvas: Canvas,
        startY: Float,
        cells: List<Pair<String, String>>,
        numRows: Int = 2,
    ): Float {
        val cardHeight = if (numRows == 1) META_CARD_1ROW_HEIGHT else (META_CARD_ROW_HEIGHT * 2) + CARD_PADDING - 2f
        val cardRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + cardHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Warm100.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, CORNER_RADIUS_CARD, CORNER_RADIUS_CARD, bgPaint)

        val labelPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_META_LABEL,
            weight = 700,
            letterSpacing = 0.07f,
        )
        val valuePaint = pdfPaint(
            color = AppColors.Gray900,
            textSize = TEXT_SIZE_META_VALUE,
            weight = 600,
            letterSpacing = 0.01f,
        )

        val colWidth = cardRect.width() / 2f
        val col0X = MARGIN + CARD_PADDING
        val col1X = MARGIN + colWidth + CARD_PADDING / 2f
        val maxCellWidth = colWidth - CARD_PADDING * 1.5f

        drawMetadataRows(canvas, startY, cells, numRows, col0X, col1X, maxCellWidth, labelPaint, valuePaint)
        val bottomGap = if (numRows == 1) 14f else CARD_BOTTOM_SPACING
        return startY + cardHeight + bottomGap
    }

    @Suppress("LongParameterList")
    private fun drawMetadataRows(
        canvas: Canvas,
        startY: Float,
        cells: List<Pair<String, String>>,
        numRows: Int,
        col0X: Float,
        col1X: Float,
        maxCellWidth: Float,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        val row0LabelY = startY + 20f
        val row0ValY = startY + 35f
        if (cells.isNotEmpty()) {
            canvas.drawText(cells[0].first, col0X, row0LabelY, labelPaint)
            canvas.drawText(truncate(valuePaint, cells[0].second, maxCellWidth), col0X, row0ValY, valuePaint)
        }
        if (cells.size > 1) {
            canvas.drawText(cells[1].first, col1X, row0LabelY, labelPaint)
            canvas.drawText(truncate(valuePaint, cells[1].second, maxCellWidth), col1X, row0ValY, valuePaint)
        }
        if (numRows > 1) {
            val row1LabelY = startY + 56f
            val row1ValY = startY + 71f
            if (cells.size > 2) {
                canvas.drawText(cells[2].first, col0X, row1LabelY, labelPaint)
                canvas.drawText(truncate(valuePaint, cells[2].second, maxCellWidth), col0X, row1ValY, valuePaint)
            }
            if (cells.size > 3) {
                canvas.drawText(cells[3].first, col1X, row1LabelY, labelPaint)
                canvas.drawText(truncate(valuePaint, cells[3].second, maxCellWidth), col1X, row1ValY, valuePaint)
            }
        }
    }

    fun calculateHeroCardHeight(speciesRowCount: Int): Float {
        val contentHeight = if (speciesRowCount == 0) {
            HERO_EMPTY_HEIGHT
        } else {
            speciesRowCount * HERO_ROW_HEIGHT
        }
        return HERO_HEADER_HEIGHT + contentHeight + HERO_PADDING_BOTTOM
    }

    fun drawMicroscopyHeroCard(
        context: Context,
        canvas: Canvas,
        params: HeroCardParams,
        startY: Float,
    ): Float {
        val headerPaint = pdfPaint(
            color = AppColors.Maroon,
            textSize = TEXT_SIZE_HERO_LABEL,
            weight = 700,
            letterSpacing = 0.08f,
        )
        val headerBaseline = startY + 10f
        canvas.drawText(context.getString(R.string.report_pdf_hero_section_title), MARGIN, headerBaseline, headerPaint)

        val cardTop = headerBaseline + HERO_LABEL_TO_CARD_GAP
        val cardHeight = calculateHeroCardHeight(params.speciesRows.size)
        val cardRect = RectF(MARGIN, cardTop, PAGE_WIDTH - MARGIN, cardTop + cardHeight)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left,
                cardRect.top,
                cardRect.left,
                cardRect.bottom,
                AppColors.Maroon.toArgb(),
                AppColors.MaroonPressed.toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(cardRect, CORNER_RADIUS_HERO, CORNER_RADIUS_HERO, cardPaint)

        val innerLeft = MARGIN + HERO_PADDING_X
        val innerRight = PAGE_WIDTH - MARGIN - HERO_PADDING_X

        val dividerY = drawHeroHeader(context, canvas, params, cardTop, innerLeft, innerRight)

        val labelPaint = pdfPaint(
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_LABEL),
            textSize = TEXT_SIZE_HERO_LABEL,
            weight = 700,
            letterSpacing = 0.08f,
        )
        val densityLabelY = dividerY + 18f
        canvas.drawText(
            context.getString(R.string.report_pdf_hero_density_header),
            innerLeft,
            densityLabelY,
            labelPaint,
        )

        if (params.speciesRows.isEmpty()) {
            val emptyPaint = pdfPaint(
                color = Color.White.copy(alpha = WHITE_ALPHA_HERO_EMPTY),
                textSize = TEXT_SIZE_HERO_EMPTY,
                weight = 400,
                letterSpacing = 0.01f,
            )
            canvas.drawText(params.emptyMessage, innerLeft, densityLabelY + 16f, emptyPaint)
        } else {
            drawHeroRows(
                context = context,
                canvas = canvas,
                rows = params.speciesRows,
                bounds = HeroRowBounds(innerLeft, innerRight, densityLabelY + 10f),
            )
        }

        return cardTop + cardHeight + 24f
    }

    @Suppress("LongParameterList")
    private fun drawHeroHeader(
        context: Context,
        canvas: Canvas,
        params: HeroCardParams,
        cardTop: Float,
        innerLeft: Float,
        innerRight: Float,
    ): Float {
        val labelPaint = pdfPaint(
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_LABEL),
            textSize = TEXT_SIZE_HERO_LABEL,
            weight = 700,
            letterSpacing = 0.08f,
        )
        val totalLabelY = cardTop + HERO_PADDING_TOP + 8f
        canvas.drawText(context.getString(R.string.report_pdf_hero_total_eggs), innerLeft, totalLabelY, labelPaint)

        val eggsStr = params.totalEggsConfirmed.toString()
        val eggCountPaint = pdfPaint(
            color = Color.White,
            textSize = TEXT_SIZE_HERO_EGGS,
            weight = 700,
            letterSpacing = -0.02f,
        )
        val eggY = totalLabelY + 36f
        canvas.drawText(eggsStr, innerLeft, eggY, eggCountPaint)

        val metaText = if (params.totalSessions != null) {
            context.getString(
                R.string.report_pdf_hero_meta_patient,
                params.speciesCount,
                params.fieldsExamined,
                params.totalSessions,
            )
        } else {
            context.getString(
                R.string.report_pdf_hero_meta_session,
                params.speciesCount,
                params.fieldsExamined,
            )
        }
        val metaPaint = pdfPaint(
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_SUBTEXT),
            textSize = TEXT_SIZE_HERO_META,
            weight = 400,
            letterSpacing = 0.01f,
        )
        val eggWidth = eggCountPaint.measureText(eggsStr)
        canvas.drawText(metaText, innerLeft + eggWidth + 12f, eggY, metaPaint)

        val dividerY = eggY + 12f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_DIVIDER).toArgb()
            strokeWidth = RULE_STROKE_WIDTH
        }
        canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, dividerPaint)
        return dividerY
    }

    private data class HeroRowBounds(
        val innerLeft: Float,
        val innerRight: Float,
        val startY: Float,
    )

    private fun drawHeroRows(
        context: Context,
        canvas: Canvas,
        rows: List<ReportPdfSpeciesRow>,
        bounds: HeroRowBounds,
    ) {
        var rowTop = bounds.startY
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_PILL).toArgb()
            style = Paint.Style.FILL
        }
        val pillTextPaint = pdfPaint(
            color = Color.White,
            textSize = TEXT_SIZE_HERO_PILL,
            weight = 600,
            letterSpacing = 0.02f,
        )
        val lpfValuePaint = pdfPaint(
            color = Color.White,
            textSize = TEXT_SIZE_HERO_LPF,
            weight = 700,
            letterSpacing = 0.01f,
        )
        val lpfUnitPaint = pdfPaint(
            color = Color.White.copy(alpha = WHITE_ALPHA_HERO_LABEL),
            textSize = TEXT_SIZE_HERO_LPF_UNIT,
            weight = 600,
            letterSpacing = 0.04f,
        )

        rows.forEach { row ->
            val isBinomial = isBinomialSpecies(row.speciesDisplayName)
            val speciesPaint = pdfPaint(
                color = Color.White,
                textSize = TEXT_SIZE_HERO_SPECIES,
                weight = 700,
                italic = isBinomial,
                letterSpacing = 0.01f,
            )
            val speciesNameY = rowTop + 13f
            canvas.drawText(row.speciesDisplayName, bounds.innerLeft, speciesNameY, speciesPaint)

            val rangeText = if (row.min == row.max) "${row.min}" else "${row.min}–${row.max}"
            val unitText = "LPF"
            val unitWidth = lpfUnitPaint.measureText(unitText)
            val rangeWidth = lpfValuePaint.measureText(rangeText)
            val gap = 3f
            val totalRightWidth = rangeWidth + gap + unitWidth
            val rightStartX = bounds.innerRight - totalRightWidth
            canvas.drawText(rangeText, rightStartX, speciesNameY, lpfValuePaint)
            canvas.drawText(unitText, rightStartX + rangeWidth + gap, speciesNameY, lpfUnitPaint)

            val descriptorText = row.descriptor?.let {
                context.getString(it.labelRes).replaceFirstChar { c -> c.uppercase() }
            }
            val burdenText = row.burdenLevel?.let {
                context.getString(it.labelRes).replaceFirstChar { c -> c.uppercase() }
            }
            val pillText = listOfNotNull(descriptorText, burdenText).joinToString(" · ")
            if (pillText.isNotEmpty()) {
                val pillTextWidth = pillTextPaint.measureText(pillText)
                val pillRect = RectF(
                    bounds.innerLeft,
                    speciesNameY + 4f,
                    bounds.innerLeft + pillTextWidth + 10f,
                    speciesNameY + 17f,
                )
                canvas.drawRoundRect(pillRect, CORNER_RADIUS_PILL, CORNER_RADIUS_PILL, pillBgPaint)
                canvas.drawText(pillText, bounds.innerLeft + 5f, speciesNameY + 14f, pillTextPaint)
            }

            rowTop += HERO_ROW_HEIGHT
        }
    }

    fun calculateSignoffHeight(organizationName: String?, reviewerEmail: String?): Float {
        var height = 10f + 32f + 16f + 12f
        if (!organizationName.isNullOrBlank()) height += 13f
        if (!reviewerEmail.isNullOrBlank()) height += 13f
        return height
    }

    fun drawReviewerSignoff(
        context: Context,
        canvas: Canvas,
        params: ReviewerSignoffParams,
        startY: Float,
    ): Float {
        val headerPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_SIGNOFF_HEADER,
            weight = 700,
            letterSpacing = 0.08f,
        )
        val headerY = startY + 10f
        canvas.drawText(context.getString(R.string.report_pdf_signoff_header), MARGIN, headerY, headerPaint)

        val lineY = headerY + 32f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray300.toArgb()
            strokeWidth = 1.2f
        }
        canvas.drawLine(MARGIN, lineY, MARGIN + SIGNATURE_LINE_WIDTH, lineY, linePaint)

        val namePaint = pdfPaint(
            color = AppColors.Gray900,
            textSize = TEXT_SIZE_SIGNOFF_NAME,
            weight = 700,
            letterSpacing = 0.01f,
        )
        val nameY = lineY + 16f
        canvas.drawText(params.reviewerName, MARGIN, nameY, namePaint)

        val subPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_SIGNOFF_SUB,
            weight = 500,
            letterSpacing = 0.01f,
        )
        val emailPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_SIGNOFF_SUB,
            weight = 400,
            letterSpacing = 0.01f,
        )
        var currentY = nameY
        if (!params.organizationName.isNullOrBlank()) {
            currentY += 13f
            canvas.drawText(params.organizationName, MARGIN, currentY, subPaint)
        }
        if (!params.reviewerEmail.isNullOrBlank()) {
            currentY += 13f
            canvas.drawText(params.reviewerEmail, MARGIN, currentY, emailPaint)
        }

        return currentY + 12f
    }

    fun drawRedesignedFooter(
        context: Context,
        canvas: Canvas,
        params: RedesignedFooterParams,
    ) {
        val ruleY = PAGE_HEIGHT - FOOTER_RULE_BOTTOM_OFFSET
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            strokeWidth = RULE_STROKE_WIDTH
        }
        canvas.drawLine(MARGIN, ruleY, PAGE_WIDTH - MARGIN, ruleY, rulePaint)

        val footerPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_FOOTER,
            weight = 500,
            letterSpacing = 0.04f,
        )
        val textY = PAGE_HEIGHT - FOOTER_TEXT_BOTTOM_OFFSET
        val shortId = params.reportId.take(SHORT_ID_LENGTH).uppercase()
        val leftText = context.getString(R.string.report_pdf_footer_report_id, shortId)
        canvas.drawText(leftText, MARGIN, textY, footerPaint)

        val rightText = if (params.isAppendix) {
            context.getString(R.string.report_pdf_footer_page_appendix, params.pageNumber, params.totalPages)
        } else {
            context.getString(R.string.report_pdf_footer_page, params.pageNumber, params.totalPages)
        }
        val rightWidth = footerPaint.measureText(rightText)
        canvas.drawText(rightText, PAGE_WIDTH - MARGIN - rightWidth, textY, footerPaint)
    }

    fun drawAppendixCard(
        context: Context,
        canvas: Canvas,
        item: ReportPdfSampleItem,
        cardRect: RectF,
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.White.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, CORNER_RADIUS_CARD, CORNER_RADIUS_CARD, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = RULE_STROKE_WIDTH
        }
        canvas.drawRoundRect(cardRect, CORNER_RADIUS_CARD, CORNER_RADIUS_CARD, borderPaint)

        val imageSize = cardRect.width() - APPENDIX_CARD_PADDING * 2
        val imageRect = RectF(
            cardRect.left + APPENDIX_CARD_PADDING,
            cardRect.top + APPENDIX_CARD_PADDING,
            cardRect.right - APPENDIX_CARD_PADDING,
            cardRect.top + APPENDIX_CARD_PADDING + (imageSize - 12f),
        )

        drawAppendixImage(context, canvas, item.imagePath, imageRect)

        val contentLeft = cardRect.left + APPENDIX_CARD_PADDING
        val contentRight = cardRect.right - APPENDIX_CARD_PADDING
        val maxTextWidth = contentRight - contentLeft

        val isBinomial = isBinomialSpecies(item.findingsSummary)
        val summaryPaint = pdfPaint(
            color = AppColors.Gray900,
            textSize = TEXT_SIZE_APPENDIX_FINDING,
            weight = 500,
            italic = isBinomial,
            letterSpacing = 0.01f,
        )
        val summaryY = imageRect.bottom + 13f
        canvas.drawText(truncate(summaryPaint, item.findingsSummary, maxTextWidth), contentLeft, summaryY, summaryPaint)

        val fieldSubPaint = pdfPaint(
            color = AppColors.Gray500,
            textSize = TEXT_SIZE_APPENDIX_FIELD,
            weight = 400,
            letterSpacing = 0.01f,
        )
        val fieldSubText = context.getString(
            R.string.report_pdf_appendix_field_subtitle,
            item.sessionLabel ?: "",
            item.fieldIndex,
        )
        val fieldSubY = summaryY + 11f
        canvas.drawText(truncate(fieldSubPaint, fieldSubText, maxTextWidth), contentLeft, fieldSubY, fieldSubPaint)

        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppColors.Gray200.toArgb()
            strokeWidth = RULE_STROKE_WIDTH
        }
        val dividerY = fieldSubY + 5f
        canvas.drawLine(contentLeft, dividerY, contentRight, dividerY, rulePaint)

        val eggsPaint = pdfPaint(
            color = AppColors.Maroon,
            textSize = TEXT_SIZE_APPENDIX_EGGS,
            weight = 700,
            letterSpacing = 0.02f,
        )
        val eggsText = context.resources.getQuantityString(
            R.plurals.report_pdf_appendix_eggs_confirmed,
            item.eggCount,
            item.eggCount,
        )
        val eggsY = dividerY + 10f
        canvas.drawText(eggsText, contentLeft, eggsY, eggsPaint)
    }

    private fun drawAppendixImage(
        context: Context,
        canvas: Canvas,
        imagePath: String?,
        imageRect: RectF,
    ) {
        val bitmap = imagePath?.let { decodeSampleBitmap(it, TARGET_IMAGE_PX) }
        if (bitmap != null) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(imageRect, CORNER_RADIUS_IMAGE, CORNER_RADIUS_IMAGE, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val srcSize = minOf(bitmap.width, bitmap.height)
            val srcX = (bitmap.width - srcSize) / 2
            val srcY = (bitmap.height - srcSize) / 2
            val srcRect = Rect(srcX, srcY, srcX + srcSize, srcY + srcSize)
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(bitmap, srcRect, imageRect, bmpPaint)
            canvas.restore()
            bitmap.recycle()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AppColors.Gray100.toArgb()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(imageRect, CORNER_RADIUS_IMAGE, CORNER_RADIUS_IMAGE, placeholderPaint)

            val titlePaint = pdfPaint(
                color = AppColors.Gray500,
                textSize = TEXT_SIZE_PLACEHOLDER_TITLE,
                weight = 700,
                letterSpacing = 0.02f,
            ).apply { textAlign = Paint.Align.CENTER }
            val subPaint = pdfPaint(
                color = AppColors.Gray400,
                textSize = TEXT_SIZE_PLACEHOLDER_SUB,
                weight = 400,
                letterSpacing = 0.01f,
            ).apply { textAlign = Paint.Align.CENTER }
            val centerX = imageRect.centerX()
            val titleY = imageRect.centerY() - 2f
            val subY = imageRect.centerY() + 11f
            canvas.drawText(
                context.getString(R.string.report_pdf_image_placeholder_title),
                centerX,
                titleY,
                titlePaint,
            )
            canvas.drawText(
                context.getString(R.string.report_pdf_image_placeholder_subtitle),
                centerX,
                subY,
                subPaint,
            )
        }
    }

    @Suppress("LongParameterList")
    fun drawAppendixPage(
        context: Context,
        canvas: Canvas,
        samplesChunk: List<ReportPdfSampleItem>,
        patientName: String,
        sessionOrScopeLabel: String,
        organizationName: String?,
        footerParams: RedesignedFooterParams,
    ) {
        val letterheadBottom = drawLetterhead(context, canvas, organizationName)
        val titleBottom = drawReportTitle(
            canvas = canvas,
            title = context.getString(R.string.report_pdf_appendix_title),
            subtitle = context.getString(R.string.report_pdf_appendix_subtitle),
            startY = letterheadBottom,
        )
        val metaCells = listOf(
            context.getString(R.string.report_pdf_meta_patient) to patientName,
            context.getString(R.string.report_pdf_meta_session_id) to sessionOrScopeLabel,
        )
        val metaBottom = drawMetadataCard(canvas, titleBottom, metaCells, numRows = 1)

        val gridTopY = metaBottom
        samplesChunk.forEachIndexed { index, item ->
            val col = index % 2
            val row = index / 2
            val left = MARGIN + col * (APPENDIX_CARD_WIDTH + APPENDIX_GRID_GAP)
            val top = gridTopY + row * (APPENDIX_CARD_HEIGHT + APPENDIX_GRID_GAP)
            val cardRect = RectF(left, top, left + APPENDIX_CARD_WIDTH, top + APPENDIX_CARD_HEIGHT)
            drawAppendixCard(context, canvas, item, cardRect)
        }

        drawRedesignedFooter(context, canvas, footerParams)
    }

    fun decodeSampleBitmap(imagePath: String, targetSizePx: Int): Bitmap? {
        val file = File(imagePath)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth > 0 && origHeight > 0) {
                var sampleSize = 1
                while (origWidth / (sampleSize * 2) >= targetSizePx && origHeight / (sampleSize * 2) >= targetSizePx) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(imagePath, decodeOptions)
            } else {
                null
            }
        }.getOrNull()
    }

    fun isBinomialSpecies(name: String): Boolean =
        name.contains(' ') && name != EggSpecies.HOOKWORM.displayName && !name.startsWith("No ")

    fun drawSectionHeader(canvas: Canvas, title: String, y: Float): Float {
        val paint = pdfPaint(AppColors.Gray900, SECTION_TEXT_SIZE, weight = 700, letterSpacing = 0.02f)
        canvas.drawText(title, MARGIN, y, paint)
        return y + LINE_HEIGHT
    }

    fun drawLabeledLines(canvas: Canvas, lines: List<Pair<String, String>>, startY: Float): Float {
        var y = startY
        lines.forEach { (label, value) -> y = drawLabeledLine(canvas, label, value, y) }
        return y
    }

    fun drawLabeledLine(canvas: Canvas, label: String, value: String, y: Float): Float {
        val labelPaint = pdfPaint(AppColors.Gray500, BODY_TEXT_SIZE, weight = 400, letterSpacing = 0.01f)
        canvas.drawText("$label:", MARGIN, y, labelPaint)
        val valuePaint = pdfPaint(AppColors.Gray900, BODY_TEXT_SIZE, weight = 400, letterSpacing = 0.01f)
        val maxValueWidth = PAGE_WIDTH - MARGIN - LABEL_COLUMN_WIDTH
        canvas.drawText(truncate(valuePaint, value, maxValueWidth), LABEL_COLUMN_WIDTH, y, valuePaint)
        return y + LINE_HEIGHT
    }

    fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val availableWidth = maxWidth - paint.measureText(ELLIPSIS)
        val fittingChars = paint.breakText(text, true, availableWidth, null)
        return text.substring(0, fittingChars) + ELLIPSIS
    }
}

internal data class HeroCardParams(
    val totalEggsConfirmed: Int,
    val speciesCount: Int,
    val fieldsExamined: Int,
    val totalSessions: Int? = null,
    val speciesRows: List<ReportPdfSpeciesRow>,
    val emptyMessage: String,
)

internal data class ReviewerSignoffParams(
    val reviewerName: String,
    val organizationName: String? = null,
    val reviewerEmail: String? = null,
)

internal data class RedesignedFooterParams(
    val reportId: String,
    val pageNumber: Int,
    val totalPages: Int,
    val isAppendix: Boolean = false,
)
