package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.agarthavision.R
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.repository.ReportPdfRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Renders [ReportPdfDocument] to an A4 PDF using `android.graphics.pdf.PdfDocument`.
 *
 * Page 1 renders the redesigned letterhead, title block, 2×2 metadata card, maroon hero card,
 * medtech reviewer sign-off, and footer. When sample images are included, subsequent pages
 * render a 2×2 grid of microscopy sample cards.
 */
class AndroidReportPdfRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReportPdfRenderer {

    override suspend fun render(document: ReportPdfDocument): ByteArray {
        val pdfDocument = PdfDocument()
        val appendixPages = if (document.appendixSamples.isEmpty()) {
            0
        } else {
            (document.appendixSamples.size + PdfChrome.SAMPLES_PER_APPENDIX_PAGE - 1) /
                PdfChrome.SAMPLES_PER_APPENDIX_PAGE
        }
        val totalPages = PAGE_NUMBER_FIRST + appendixPages

        val pageInfo1 = PdfDocument.PageInfo
            .Builder(PdfChrome.PAGE_WIDTH, PdfChrome.PAGE_HEIGHT, PAGE_NUMBER_FIRST)
            .create()
        val page1 = pdfDocument.startPage(pageInfo1)
        drawSummaryPage(page1.canvas, document, totalPages)
        pdfDocument.finishPage(page1)

        if (document.appendixSamples.isNotEmpty()) {
            val chunks = document.appendixSamples.chunked(PdfChrome.SAMPLES_PER_APPENDIX_PAGE)
            chunks.forEachIndexed { index, chunk ->
                val pageNumber = PAGE_OFFSET_APPENDIX + index
                val pageInfo = PdfDocument.PageInfo
                    .Builder(PdfChrome.PAGE_WIDTH, PdfChrome.PAGE_HEIGHT, pageNumber)
                    .create()
                val page = pdfDocument.startPage(pageInfo)
                PdfChrome.drawAppendixPage(
                    context = context,
                    canvas = page.canvas,
                    samplesChunk = chunk,
                    patientName = document.header.patientName,
                    sessionOrScopeLabel = document.header.sessionLabel ?: document.header.sessionId,
                    organizationName = document.header.organizationName,
                    footerParams = RedesignedFooterParams(
                        reportId = document.header.reportId,
                        pageNumber = pageNumber,
                        totalPages = totalPages,
                        isAppendix = true,
                    ),
                )
                pdfDocument.finishPage(page)
            }
        }

        return ByteArrayOutputStream().use { output ->
            pdfDocument.writeTo(output)
            pdfDocument.close()
            output.toByteArray()
        }
    }

    private fun drawSummaryPage(canvas: Canvas, document: ReportPdfDocument, totalPages: Int) {
        val letterheadBottom = PdfChrome.drawLetterhead(context, canvas, document.header.organizationName)

        val subtitle = if (document.appendixSamples.isNotEmpty()) {
            context.getString(R.string.report_pdf_subtitle_with_appendix, PAGE_OFFSET_APPENDIX)
        } else {
            context.getString(R.string.report_pdf_subtitle_standard)
        }
        val titleBottom = PdfChrome.drawReportTitle(
            canvas = canvas,
            title = context.getString(R.string.report_pdf_main_title),
            subtitle = subtitle,
            startY = letterheadBottom,
        )

        val sexLabel = context.getString(PdfChrome.sexLabelRes(document.header.patientSex))
        val ageValue = context.getString(R.string.report_pdf_age_value, document.header.patientAgeYears)
        val examDate = DateTimeFormatter.ofPattern(DATE_PATTERN)
            .withZone(ZoneId.systemDefault())
            .format(document.header.generatedAt)
        val metadataCells = listOf(
            context.getString(R.string.report_pdf_meta_patient) to document.header.patientName,
            context.getString(R.string.report_pdf_meta_session_id) to
                (document.header.sessionLabel ?: document.header.sessionId),
            context.getString(R.string.report_pdf_meta_patient_details) to
                "$sexLabel · $ageValue · ${document.header.barangayLabel}",
            context.getString(R.string.report_pdf_meta_exam_date) to examDate,
        )
        val metaBottom = PdfChrome.drawMetadataCard(canvas, titleBottom, metadataCells, numRows = 2)

        val emptyMessage = context.resources.getQuantityString(
            R.plurals.report_pdf_no_parasites_fields,
            document.header.fieldsExamined,
            document.header.fieldsExamined,
        )
        val heroParams = HeroCardParams(
            totalEggsConfirmed = document.header.totalEggsConfirmed,
            speciesCount = document.header.positiveSpecies.size,
            fieldsExamined = document.header.fieldsExamined,
            totalSessions = null,
            speciesRows = document.speciesRows,
            emptyMessage = emptyMessage,
        )
        val heroBottom = PdfChrome.drawMicroscopyHeroCard(context, canvas, heroParams, metaBottom)

        val signoffParams = ReviewerSignoffParams(
            reviewerName = document.header.generatedByName,
            organizationName = document.header.organizationName,
            reviewerEmail = document.header.generatedByEmail,
        )
        PdfChrome.drawReviewerSignoff(context, canvas, signoffParams, heroBottom)

        val footerParams = RedesignedFooterParams(
            reportId = document.header.reportId,
            pageNumber = PAGE_NUMBER_FIRST,
            totalPages = totalPages,
            isAppendix = false,
        )
        PdfChrome.drawRedesignedFooter(context, canvas, footerParams)
    }

    private companion object {
        private const val PAGE_NUMBER_FIRST = 1
        private const val PAGE_OFFSET_APPENDIX = 2
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    }
}
