package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PatientReportPdfRenderer
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.sync.SyncScheduler
import com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Generates a persisted patient report and writes the patient-facing PDF to device storage.
 *
 * Pools every session [scope] resolves to into one document (D3): findings are aggregated
 * across all of them into a single [aggregateLpfPerSpecies] call, while the per-session
 * breakdown table on the PDF keeps each smear's own range visible. Sessions with zero verified
 * samples are dropped from both (D6); if none are left the whole generation fails.
 *
 * Mirrors [GenerateSessionReportUseCase]'s shape and DI surface, at the patient scope.
 */
// Composition-root use case wiring the same class of independently meaningful dependencies as
// GenerateSessionReportUseCase, plus PatientRepository/PatientReportPdfBuilder/Renderer.
@Suppress("LongParameterList")
class GeneratePatientReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val findingDao: SampleSpeciesFindingDao,
    private val reportRepository: ReportRepository,
    private val reportFileStore: ReportFileStore,
    private val patientReportPdfBuilder: PatientReportPdfBuilder,
    private val patientReportPdfRenderer: PatientReportPdfRenderer,
    private val syncReportUseCase: SyncReportUseCase,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        patientId: String,
        scope: PatientReportScope = PatientReportScope(),
    ): Result<Report> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val patient = requireNotNull(patientRepository.getPatientById(patientId)) {
            PATIENT_NOT_ON_DEVICE_MESSAGE
        }

        val allSessions = sessionRepository.getSessionsForPatient(patientId, userId)
        val candidateSessions = resolvePatientReportSessions(
            sessions = allSessions,
            scope = scope,
            zone = ZoneId.systemDefault(),
        )

        val breakdown = buildSessionBreakdown(candidateSessions, userId)
        val sessionRows = breakdown.sessionRows
        require(sessionRows.isNotEmpty()) { NO_VERIFIED_SAMPLES_MESSAGE }
        val totalSamples = breakdown.totalSamples
        val totalEggsConfirmed = breakdown.totalEggsConfirmed

        val reportId = UUID.randomUUID().toString()
        // One instant for the report's timestamp, the patient's age, and the PDF's
        // "Generated at" line, mirroring GenerateSessionReportUseCase.
        val generatedAt = Instant.now()
        val generatedByName = resolveGeneratedByName(userId)
        val barangay = psgcRepository.getBarangay(patient.psgcBarangayCode)
        val address = barangay?.fullAddress ?: patient.psgcBarangayCode

        // Pooled across every included session (D3) — the headline figure a patient report
        // exists to show; the per-session table above is where a single smear's own range
        // stays visible.
        val pooledLpfPerSpecies = aggregateLpfPerSpecies(breakdown.allFindings, totalSamples)
        val positiveSpecies = pooledLpfPerSpecies.filterValues { it.max > 0 }.keys.sorted()

        val pdfDoc = patientReportPdfBuilder.build(
            reportId = reportId,
            patientDisplayName = patient.displayName,
            ageYears = patient.ageYears(generatedAt),
            sex = patient.sex,
            address = address,
            scope = scope,
            generatedByName = generatedByName,
            generatedAt = generatedAt,
            pooledLpfPerSpecies = pooledLpfPerSpecies,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            sessionRows = sessionRows,
        )
        val pdfBytes = patientReportPdfRenderer.render(pdfDoc)
        val pdfPath = reportFileStore.writePatientPdf(reportId, patientId, pdfBytes)

        val report = Report(
            id = reportId,
            sessionId = null,
            patientId = patientId,
            sessionIds = sessionRows.map { it.sessionId },
            userId = userId,
            reportType = ReportType.PATIENT,
            generatedAt = generatedAt,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            positiveSpecies = positiveSpecies,
            lpfPerSpecies = pooledLpfPerSpecies,
            csvFilePath = null,
            pdfFilePath = pdfPath,
            supabaseStatus = ReportSyncStatus.PENDING,
        )
        reportRepository.insert(report)
        syncReportUseCase(reportId)
        syncScheduler.requestSync()
        report
    }

    /** The per-session rows, pooled findings, and running totals [invoke] builds the report from. */
    private class SessionBreakdown(
        val sessionRows: List<PatientReportSessionRow>,
        val allFindings: List<SampleSpeciesFindingEntity>,
        val totalSamples: Int,
        val totalEggsConfirmed: Int,
    )

    /**
     * Walks [candidateSessions] oldest-first, dropping any with zero verified samples (D6), and
     * returns one row per session that survives plus the pooled findings behind it.
     */
    private suspend fun buildSessionBreakdown(
        candidateSessions: List<Session>,
        userId: String,
    ): SessionBreakdown {
        val sessionRows = mutableListOf<PatientReportSessionRow>()
        val allFindings = mutableListOf<SampleSpeciesFindingEntity>()
        var totalSamples = 0
        var totalEggsConfirmed = 0

        for (session in candidateSessions.sortedBy { it.startedAt }) {
            val samples = sampleRepository.getSamplesForSession(session.id, userId)
            // Zero-sample sessions are dropped, not reported as a zero row (D6): a session
            // nobody verified anything in was never examined, and including it would let an
            // empty smear pad the "sessions covered" count on a clinical document.
            if (samples.isEmpty()) continue

            val eggCounts = detectionRepository.getConfirmedEggCountsForSession(session.id, userId)
            val findings = findingDao.getFindingsForSession(session.id, userId)
            allFindings += findings

            val sessionLpf = aggregateLpfPerSpecies(findings, samples.size)
            val sessionEggsConfirmed = eggCounts.sumOf { it.count }
            totalSamples += samples.size
            totalEggsConfirmed += sessionEggsConfirmed

            sessionRows += PatientReportSessionRow(
                sessionId = session.id,
                sessionLabel = session.label,
                startedAt = Instant.ofEpochMilli(session.startedAt),
                sampleCount = samples.size,
                eggsConfirmed = sessionEggsConfirmed,
                positiveSpecies = sessionLpf.filterValues { it.max > 0 }.keys.sorted(),
                lpfPerSpecies = sessionLpf,
            )
        }

        return SessionBreakdown(sessionRows, allFindings, totalSamples, totalEggsConfirmed)
    }

    private suspend fun resolveGeneratedByName(userId: String): String {
        val identity = authRepository.observeLocalIdentity().first()
        return identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.email?.takeIf { it.isNotBlank() }
            ?: userId
    }

    companion object {
        const val NO_VERIFIED_SAMPLES_MESSAGE = "Verify at least one sample to generate a report."
        const val PATIENT_NOT_ON_DEVICE_MESSAGE =
            "This patient is not on this device yet. Sync, then try again."
    }
}
