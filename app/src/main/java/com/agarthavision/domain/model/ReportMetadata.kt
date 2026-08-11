package com.agarthavision.domain.model

import java.time.Instant

/**
 * Bundles a session report's identity and summary numbers for CSV generation. Groups the
 * fields shared by [com.agarthavision.domain.usecase.records.ReportCsvBuilder]'s constructor
 * and its header-block formatting, which previously traveled as loose parameters.
 */
data class ReportMetadata(
    val reportId: String,
    val session: Session,
    val generatedBy: String,
    val generatedAt: Instant,
    val totalSamples: Int,
    val totalEggsConfirmed: Int,
    val positiveSpecies: List<String>,
    val epgPerSpecies: Map<String, Int>,
)
