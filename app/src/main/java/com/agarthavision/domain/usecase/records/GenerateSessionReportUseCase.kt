package com.agarthavision.domain.usecase.records

import com.agarthavision.core.util.EpgCalculator
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportFormat
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.data.supabase.SyncReportUseCase
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Generates a persisted session report and writes the CSV + PDF to device storage.
 */
// Composition-root use case wiring 10 distinct, non-overlapping DI dependencies (repositories,
// file store, CSV/PDF builders + renderer, sync use case); each is independently meaningful and
// bundling would not simplify the real dependency graph.
@Suppress("LongParameterList")
class GenerateSessionReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val reportRepository: ReportRepository,
    private val reportFileStore: ReportFileStore,
    private val reportCsvBuilder: ReportCsvBuilder,
    private val reportPdfBuilder: ReportPdfBuilder,
    private val reportPdfRenderer: ReportPdfRenderer,
    private val syncReportUseCase: SyncReportUseCase,
) {
    suspend operator fun invoke(sessionId: String, format: ReportFormat): Result<Report> = runCatching {
        val userId = requireNotNull(authRepository.getCurrentUserId()) {
            "A logged-in medtech is required to generate a report."
        }
        val session = requireNotNull(sessionRepository.getSessionById(sessionId)) {
            "Session $sessionId does not exist."
        }
        require(session.userId == userId) {
            "Session $sessionId is not owned by the current user."
        }

        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        val detectionsBySample = samples.associate { sample ->
            sample.id to detectionRepository.getDetectionsForSample(sample.id)
        }

        val eggCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val normalizedCounts = eggCounts.groupBy { it.canonicalSpecies() }.mapValues { entry ->
            entry.value.sumOf { it.count }
        }
        // TEMPORARY (86d4a6jxw): epgPerSpecies is the reported per-species number for both the
        // CSV and PDF. It will be replaced by LPF (Low Power Field) density once that ticket's
        // pipeline lands; until then every consumer of this map must keep labeling it "EPG".
        val epgPerSpecies = normalizedCounts.mapValues { EpgCalculator.epg(it.value) }
        val positiveSpecies = epgPerSpecies.filterValues { it > 0 }.keys.sorted()
        val totalEggsConfirmed = normalizedCounts.values.sum()
        val totalSamples = samples.count { !it.isRepeat }

        val reportId = UUID.randomUUID().toString()
        val generatedAt = Instant.now()
        val metadata = ReportMetadata(
            reportId = reportId,
            session = session,
            generatedBy = userId,
            generatedAt = generatedAt,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            epgPerSpecies = epgPerSpecies,
        )
        // Generate only the format the medtech asked for, so the report carries a single file
        // and its format is unambiguous everywhere it's shown, opened, or shared.
        var csvFilePath: String? = null
        var pdfFilePath: String? = null
        when (format) {
            ReportFormat.CSV -> {
                val csv = reportCsvBuilder.build(
                    metadata = metadata,
                    samples = samples,
                    detectionsBySample = detectionsBySample,
                )
                csvFilePath = reportFileStore.writeCsv(reportId, sessionId, csv)
            }

            ReportFormat.PDF -> {
                val pdfDocument = reportPdfBuilder.build(
                    metadata = metadata,
                    samples = samples,
                    detectionsBySample = detectionsBySample,
                )
                val pdfBytes = reportPdfRenderer.render(pdfDocument)
                pdfFilePath = reportFileStore.writePdf(reportId, sessionId, pdfBytes)
            }
        }

        val report = Report(
            id = reportId,
            sessionId = sessionId,
            userId = userId,
            reportType = ReportType.SESSION,
            generatedAt = generatedAt,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            epgPerSpecies = epgPerSpecies,
            csvFilePath = csvFilePath,
            pdfFilePath = pdfFilePath,
            supabaseStatus = ReportSyncStatus.PENDING,
        )
        reportRepository.insert(report)
        syncReportUseCase(reportId)
        report
    }
}
