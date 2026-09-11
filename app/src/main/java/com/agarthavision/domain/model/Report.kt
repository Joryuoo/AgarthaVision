package com.agarthavision.domain.model

import java.time.Instant

/**
 * Domain model for a persisted session report.
 *
 * One row per report generation event. Multiple reports per session are
 * allowed (ordered by [generatedAt] descending). The CSV and PDF files at
 * [csvFilePath] / [pdfFilePath] are local-only; only the metadata + aggregate stats here
 * round-trip to Supabase.
 *
 * See schema.ts and CONTEXT.md.
 */
data class Report(
    val id: String,
    val sessionId: String,
    val userId: String,
    val reportType: ReportType,
    val generatedAt: Instant,
    val totalSamples: Int,
    val totalEggsConfirmed: Int,
    /** Canonical species names (per [EggSpecies.canonicalClass]) with at least one confirmed egg. */
    val positiveSpecies: List<String>,
    /**
     * Canonical species name → EPG integer. Sourced from EpgCalculator × per-species counts.
     *
     * Temporary metric: ticket 86d4a6jxw replaces EPG with LPF density once that pipeline lands.
     */
    val epgPerSpecies: Map<String, Int>,
    val csvFilePath: String?,
    /** Local path (or `content://` URI) to the patient-facing PDF, mirroring [csvFilePath]. */
    val pdfFilePath: String?,
    val supabaseStatus: ReportSyncStatus,
)
