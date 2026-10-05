package com.agarthavision.domain.model

import java.time.Instant

/**
 * Bundles a session report's identity, patient, and summary numbers for PDF generation. Groups
 * the fields shared by [com.agarthavision.domain.usecase.records.ReportPdfBuilder]'s constructor
 * and its header-block formatting, which previously traveled as loose parameters.
 */
data class ReportMetadata(
    val reportId: String,
    val session: Session,
    val patient: ReportPatient,
    /** Supabase user id of the medtech who generated the report. */
    val generatedBy: String,
    /**
     * The medtech's display name (or email, or user id — see
     * [com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase]).
     */
    val generatedByName: String,
    val generatedAt: Instant,
    /** Fields examined — one per verified sample. */
    val totalSamples: Int,
    val totalEggsConfirmed: Int,
    val positiveSpecies: List<String>,
    val lpfPerSpecies: Map<String, LpfDensity>,
)
