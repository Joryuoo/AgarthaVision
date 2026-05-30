package com.agarthavision.domain.usecase.records

import com.agarthavision.core.util.EpgCalculator
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.data.supabase.SyncReportUseCase
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Generates a persisted session report and writes the CSV to device storage.
 */
class GenerateSessionReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val reportRepository: ReportRepository,
    private val reportFileStore: ReportFileStore,
    private val reportCsvBuilder: ReportCsvBuilder,
    private val syncReportUseCase: SyncReportUseCase,
) {
    suspend operator fun invoke(sessionId: String): Result<Report> = runCatching {
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
        val epgPerSpecies = normalizedCounts.mapValues { EpgCalculator.epg(it.value) }
        val positiveSpecies = epgPerSpecies.filterValues { it > 0 }.keys.sorted()
        val totalEggsConfirmed = normalizedCounts.values.sum()
        val totalSamples = samples.count { !it.isRepeat }

        val reportId = UUID.randomUUID().toString()
        val generatedAt = Instant.now()
        val csv = reportCsvBuilder.build(
            reportId = reportId,
            session = session,
            generatedBy = userId,
            generatedAt = generatedAt,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            epgPerSpecies = epgPerSpecies,
            samples = samples,
            detectionsBySample = detectionsBySample,
        )
        val csvFilePath = reportFileStore.writeCsv(reportId, sessionId, csv)

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
            supabaseStatus = ReportSyncStatus.PENDING,
        )
        reportRepository.insert(report)
        syncReportUseCase(reportId)
        report
    }

    private fun com.agarthavision.domain.model.EggCount.canonicalSpecies(): String {
        val resolved = EggSpecies.fromClassLabel(species)?.canonicalClass
        return resolved ?: species
    }
}
