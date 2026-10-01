package com.agarthavision.domain.repository

import com.agarthavision.domain.model.PatientReportPdfDocument

/**
 * Renders a [PatientReportPdfDocument] to bytes of a PDF file.
 *
 * Pure Kotlin interface (no `android.*`, per C2), mirroring [ReportPdfRenderer] at the patient
 * scope; only the Android implementation (`data/repository/AndroidPatientReportPdfRenderer.kt`)
 * touches `android.graphics.pdf`.
 */
interface PatientReportPdfRenderer {
    /** Renders [document] to a complete PDF file body. */
    suspend fun render(document: PatientReportPdfDocument): ByteArray
}
