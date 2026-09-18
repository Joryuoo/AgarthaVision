package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportFormat
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Generates a persisted session report and writes the CSV + PDF to device storage.
 */
// Composition-root use case wiring 11 distinct, non-overlapping DI dependencies (repositories,
// file store, CSV/PDF builders + renderer, sync use case); each is independently meaningful and
// bundling would not simplify the real dependency graph.
@Suppress("LongParameterList")
class GenerateSessionReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val findingDao: SampleSpeciesFindingDao,
    private val reportRepository: ReportRepository,
    private val reportFileStore: ReportFileStore,
    private val reportCsvBuilder: ReportCsvBuilder,
    private val reportPdfBuilder: ReportPdfBuilder,
    private val reportPdfRenderer: ReportPdfRenderer,
    private val syncReportUseCase: SyncReportUseCase,
) {
    suspend operator fun invoke(sessionId: String, format: ReportFormat): Result<Report> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val session = requireNotNull(sessionRepository.getSessionById(sessionId)) {
            "Session $sessionId does not exist."
        }
        require(session.userId == userId) {
            "Session $sessionId is not owned by the current user."
        }

        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        val fieldCount = samples.size.coerceAtLeast(1)
        val detectionsBySample = samples.associate { sample ->
            sample.id to detectionRepository.getDetectionsForSample(sample.id)
        }

        val eggCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val findings = findingDao.getFindingsForSession(sessionId, userId)
        val lpfPerSpecies = computeLpfDensity(findings, fieldCount)

        val reportId = UUID.randomUUID().toString()
        val generatedAt = Instant.now()
        val metadata = ReportMetadata(
            reportId = reportId,
            session = session,
            generatedBy = userId,
            generatedAt = generatedAt,
            totalSamples = samples.size,
            totalEggsConfirmed = eggCounts.sumOf { it.count },
            positiveSpecies = lpfPerSpecies.filterValues { it.mean > 0 }.keys.sorted(),
            lpfPerSpecies = lpfPerSpecies,
        )

        val (csvPath, pdfPath) = generateFiles(format, metadata, samples, detectionsBySample)

        val report = Report(
            id = reportId,
            sessionId = sessionId,
            userId = userId,
            reportType = ReportType.SESSION,
            generatedAt = generatedAt,
            totalSamples = metadata.totalSamples,
            totalEggsConfirmed = metadata.totalEggsConfirmed,
            positiveSpecies = metadata.positiveSpecies,
            lpfPerSpecies = lpfPerSpecies,
            csvFilePath = csvPath,
            pdfFilePath = pdfPath,
            supabaseStatus = ReportSyncStatus.PENDING,
        )
        reportRepository.insert(report)
        syncReportUseCase(reportId)
        report
    }

    private fun computeLpfDensity(
        findings: List<SampleSpeciesFindingEntity>,
        fieldCount: Int,
    ): Map<String, LpfDensity> = findings.groupBy { it.species }.mapValues { (_, speciesFindings) ->
        val countsByField = speciesFindings.groupBy { it.sampleId }
            .mapValues { it.value.sumOf { f -> f.eggCount } }

        val totalEggs = countsByField.values.sum()
        val maxEggs = countsByField.values.maxOrNull() ?: 0
        val minEggs = if (countsByField.size < fieldCount) 0 else countsByField.values.minOrNull() ?: 0

        LpfDensity(
            mean = totalEggs.toFloat() / fieldCount,
            min = minEggs,
            max = maxEggs
        )
    }

    private suspend fun generateFiles(
        format: ReportFormat,
        metadata: ReportMetadata,
        samples: List<Sample>,
        detectionsBySample: Map<String, List<Detection>>,
    ): Pair<String?, String?> {
        var csvFilePath: String? = null
        var pdfFilePath: String? = null
        when (format) {
            ReportFormat.CSV -> {
                val csv = reportCsvBuilder.build(metadata, samples, detectionsBySample)
                csvFilePath = reportFileStore.writeCsv(metadata.reportId, metadata.session.id, csv)
            }
            ReportFormat.PDF -> {
                val pdfDoc = reportPdfBuilder.build(metadata, samples, detectionsBySample)
                val pdfBytes = reportPdfRenderer.render(pdfDoc)
                pdfFilePath = reportFileStore.writePdf(metadata.reportId, metadata.session.id, pdfBytes)
            }
        }
        return csvFilePath to pdfFilePath
    }
}
