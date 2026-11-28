package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportPdfSampleItem
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PatientReportPdfRenderer
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Previews an in-memory patient report PDF without persisting to storage or database.
 */
@Suppress("LongParameterList")
class PreviewPatientReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val findingDao: SampleSpeciesFindingDao,
    private val patientReportPdfBuilder: PatientReportPdfBuilder,
    private val patientReportPdfRenderer: PatientReportPdfRenderer,
) {
    suspend operator fun invoke(
        patientId: String,
        scope: PatientReportScope = PatientReportScope(),
        config: ReportConfig = ReportConfig(),
    ): Result<ByteArray> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val patient = requireNotNull(patientRepository.getPatientById(patientId)) {
            GeneratePatientReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE
        }

        val allSessions = sessionRepository.getSessionsForPatient(patientId, userId)
        val candidateSessions = resolvePatientReportSessions(
            sessions = allSessions,
            scope = scope,
            zone = ZoneId.systemDefault(),
        )

        val breakdown = buildSessionBreakdown(candidateSessions, userId)
        val sessionRows = breakdown.sessionRows
        require(sessionRows.isNotEmpty()) { GeneratePatientReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE }
        val totalSamples = breakdown.totalSamples
        val totalEggsConfirmed = breakdown.totalEggsConfirmed

        val reportId = UUID.randomUUID().toString()
        val generatedAt = Instant.now()
        val identity = authRepository.observeLocalIdentity().first()
        val generatedByName = resolveGeneratedByName(userId, identity)
        val barangay = psgcRepository.getBarangay(patient.psgcBarangayCode)
        val address = barangay?.fullAddress ?: patient.psgcBarangayCode

        val pooledLpfPerSpecies = aggregateLpfPerSpecies(breakdown.allFindings, totalSamples)
        val positiveSpecies = pooledLpfPerSpecies.filterValues { it.max > 0 }.keys.sorted()

        val formattedPatientName = formatPatientNameForReport(patient, config.patientNameOption)
        val appendixSamples = buildAppendixSamplesIfNeeded(breakdown, config)

        val pdfDoc = patientReportPdfBuilder.build(
            reportId = reportId,
            patientDisplayName = formattedPatientName,
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
            config = config,
            generatedByEmail = identity?.email,
            organizationName = deriveOrganizationFromEmail(identity?.email),
            appendixSamples = appendixSamples,
        )
        patientReportPdfRenderer.render(pdfDoc)
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

    private suspend fun buildAppendixSamplesIfNeeded(
        breakdown: SessionBreakdown,
        config: ReportConfig,
    ): List<ReportPdfSampleItem> {
        if (!config.includeSampleImages) return emptyList()
        val detectionsBySample = breakdown.verifiedSamples.associate { sample ->
            sample.id to detectionRepository.getDetectionsForSample(sample.id)
        }
        val sessionLabelById = breakdown.includedSessions.associate { session ->
            session.id to (session.label ?: session.id)
        }
        return buildAppendixSamples(
            samples = breakdown.verifiedSamples,
            detectionsBySample = detectionsBySample,
            sessionLabelById = sessionLabelById,
        )
    }

    private class SessionBreakdown(
        val sessionRows: List<PatientReportSessionRow>,
        val allFindings: List<SampleSpeciesFindingEntity>,
        val totalSamples: Int,
        val totalEggsConfirmed: Int,
        val includedSessions: List<Session>,
        val verifiedSamples: List<Sample>,
    )

    private suspend fun buildSessionBreakdown(
        candidateSessions: List<Session>,
        userId: String,
    ): SessionBreakdown {
        val sessionRows = mutableListOf<PatientReportSessionRow>()
        val allFindings = mutableListOf<SampleSpeciesFindingEntity>()
        val includedSessions = mutableListOf<Session>()
        val verifiedSamples = mutableListOf<Sample>()
        var totalSamples = 0
        var totalEggsConfirmed = 0

        for (session in candidateSessions.sortedBy { it.startedAt }) {
            val samples = sampleRepository.getSamplesForSession(session.id, userId)
            if (samples.isEmpty()) continue

            includedSessions += session
            verifiedSamples += samples

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

        return SessionBreakdown(
            sessionRows = sessionRows,
            allFindings = allFindings,
            totalSamples = totalSamples,
            totalEggsConfirmed = totalEggsConfirmed,
            includedSessions = includedSessions,
            verifiedSamples = verifiedSamples,
        )
    }

    private fun resolveGeneratedByName(userId: String, identity: LocalIdentity?): String {
        return identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.email?.takeIf { it.isNotBlank() }
            ?: userId
    }
}
