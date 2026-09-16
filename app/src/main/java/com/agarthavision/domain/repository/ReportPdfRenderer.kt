package com.agarthavision.domain.repository

import com.agarthavision.domain.model.ReportPdfDocument

/**
 * Renders a [ReportPdfDocument] to bytes of a PDF file.
 *
 * The interface is pure Kotlin (no `android.*`, per C2) so [ReportPdfDocument] construction and
 * layout math stay JVM-testable; only the Android implementation
 * (`data/repository/AndroidReportPdfRenderer.kt`) touches `android.graphics.pdf`.
 */
interface ReportPdfRenderer {
    /**
     * Renders [document] to a complete PDF file body.
     */
    suspend fun render(document: ReportPdfDocument): ByteArray
}
