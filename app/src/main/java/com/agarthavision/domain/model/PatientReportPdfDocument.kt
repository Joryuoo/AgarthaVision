package com.agarthavision.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Pure-data description of one patient-report PDF, produced by
 * [com.agarthavision.domain.usecase.records.PatientReportPdfBuilder] and consumed by
 * [com.agarthavision.domain.repository.PatientReportPdfRenderer].
 *
 * Mirrors [ReportPdfDocument]'s shape at the patient scope: [speciesRows] carries the pooled
 * LPF range across every included session (D3), and [sessionRows] carries the per-session
 * breakdown table so a medtech can see which smear drove a given finding.
 */
data class PatientReportPdfDocument(
    val header: PatientReportPdfHeader,
    val speciesRows: List<ReportPdfSpeciesRow>,
    val sessionRows: List<PatientReportSessionRow>,
)

/**
 * The report's identity, patient, scope, and summary numbers, rendered as the PDF's header
 * block.
 */
data class PatientReportPdfHeader(
    val reportId: String,
    val patientDisplayName: String,
    val ageYears: Int,
    /** Null prints `patients_sex_unknown`. */
    val sex: Sex?,
    val address: String,
    val scopeStart: LocalDate?,
    val scopeEnd: LocalDate?,
    /** The medtech's display name (or email, or user id) who generated the report. */
    val generatedBy: String,
    val generatedAt: Instant,
    val totalSessions: Int,
    val totalSamples: Int,
    val totalEggsConfirmed: Int,
    /** Canonical species names (per [EggSpecies.canonicalClass]) with at least one confirmed egg. */
    val positiveSpecies: List<String>,
)

/** One row of the per-session breakdown table. */
data class PatientReportSessionRow(
    val sessionId: String,
    val sessionLabel: String?,
    val startedAt: Instant,
    val sampleCount: Int,
    val eggsConfirmed: Int,
    val positiveSpecies: List<String>,
    val lpfPerSpecies: Map<String, LpfDensity>,
)
