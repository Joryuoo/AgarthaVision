package com.agarthavision.domain.model

import java.time.Instant

/**
 * Domain model for a persisted session report.
 *
 * One row per report generation event. Multiple reports per session are
 * allowed (ordered by [generatedAt] descending). The CSV file at
 * [csvFilePath] is local-only; only the metadata + aggregate stats here
 * round-trip to Supabase.
 *
 * See docs/ERD.md (Module 3.1) and the Phase 1 REPORTS rollout in
 * mutable-growing-graham.md.
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
    /** Canonical species name → EPG integer. Sourced from EpgCalculator × per-species counts. */
    val epgPerSpecies: Map<String, Int>,
    val csvFilePath: String?,
    val supabaseStatus: ReportSyncStatus,
)
