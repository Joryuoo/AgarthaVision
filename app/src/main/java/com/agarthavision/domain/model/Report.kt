package com.agarthavision.domain.model

import java.time.Instant

/**
 * Domain model for a persisted report, scoped to either one session or one patient.
 *
 * One row per report generation event. Multiple reports per session/patient are
 * allowed (ordered by [generatedAt] descending). The PDF file at [pdfFilePath] is local-only;
 * only the metadata + aggregate stats here round-trip to Supabase. [csvFilePath] is a legacy
 * column, read only on reports generated before 86d4be47c — no report writes one now.
 *
 * A session report has [sessionId] set and [patientId]/[sessionIds] empty; a patient report
 * (14zcqntj2uz) has [patientId] set, [sessionId] null, and [sessionIds] naming the sessions
 * pooled into this snapshot.
 *
 * See schema.ts and CONTEXT.md.
 */
data class Report(
    val id: String,
    val sessionId: String?,
    val userId: String,
    val patientId: String? = null,
    /** Session ids pooled into this report. Non-empty on a patient report only. */
    val sessionIds: List<String> = emptyList(),
    val reportType: ReportType,
    val generatedAt: Instant,
    val totalSamples: Int,
    val totalEggsConfirmed: Int,
    /** Canonical species names (per [EggSpecies.canonicalClass]) with at least one confirmed egg. */
    val positiveSpecies: List<String>,
    /**
     * Canonical species name → LPF density metrics.
     *
     * Replaces EPG (86d4a6jxw): Direct Smear density is the primary reported unit.
     */
    val lpfPerSpecies: Map<String, LpfDensity>,
    val csvFilePath: String?,
    /** Local path (or `content://` URI) to the patient-facing PDF, mirroring [csvFilePath]. */
    val pdfFilePath: String?,
    val supabaseStatus: ReportSyncStatus,
    val sessionLabel: String? = null,
    val patientName: String? = null,
)
