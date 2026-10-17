package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportPdfHeader
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.Sex
import java.time.Instant
import javax.inject.Inject

/**
 * Builds the pure-data [PatientReportPdfDocument] handed to
 * [com.agarthavision.domain.repository.PatientReportPdfRenderer].
 *
 * The pooled [PatientReportPdfDocument.speciesRows] use the same filter/sort rule as
 * [ReportPdfBuilder]'s session-scoped table (`buildReportSpeciesRows`) so the two documents
 * never describe the same underlying findings differently.
 */
class PatientReportPdfBuilder @Inject constructor() {
    @Suppress("LongParameterList")
    fun build(
        reportId: String,
        patientDisplayName: String,
        ageYears: Int,
        sex: Sex?,
        address: String,
        scope: PatientReportScope,
        generatedByName: String,
        generatedAt: Instant,
        pooledLpfPerSpecies: Map<String, LpfDensity>,
        totalSamples: Int,
        totalEggsConfirmed: Int,
        positiveSpecies: List<String>,
        sessionRows: List<PatientReportSessionRow>,
    ): PatientReportPdfDocument {
        val header = PatientReportPdfHeader(
            reportId = reportId,
            patientDisplayName = patientDisplayName,
            ageYears = ageYears,
            sex = sex,
            address = address,
            scopeStart = scope.startDate,
            scopeEnd = scope.endDate,
            generatedBy = generatedByName,
            generatedAt = generatedAt,
            totalSessions = sessionRows.size,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
        )
        return PatientReportPdfDocument(
            header = header,
            speciesRows = buildReportSpeciesRows(pooledLpfPerSpecies),
            sessionRows = sessionRows,
        )
    }
}
