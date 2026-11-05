package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPatient
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Previews an in-memory session report PDF without persisting to storage or database.
 */
@Suppress("LongParameterList")
class PreviewSessionReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val findingDao: SampleSpeciesFindingDao,
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val reportPdfBuilder: ReportPdfBuilder,
    private val reportPdfRenderer: ReportPdfRenderer,
) {
    suspend operator fun invoke(
        sessionId: String,
        config: ReportConfig = ReportConfig(),
    ): Result<ByteArray> = runCatching {
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
        require(samples.isNotEmpty()) { GenerateSessionReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE }

        val patient = requireNotNull(patientRepository.getPatientById(session.patientId)) {
            GenerateSessionReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE
        }

        val fieldCount = samples.size
        val detectionsBySample = samples.associate { sample ->
            sample.id to detectionRepository.getDetectionsForSample(sample.id)
        }

        val eggCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val findings = findingDao.getFindingsForSession(sessionId, userId)
        val lpfPerSpecies = aggregateLpfPerSpecies(findings, fieldCount)

        val reportId = UUID.randomUUID().toString()
        val generatedAt = Instant.now()
        val identity = authRepository.observeLocalIdentity().first()
        val generatedByName = resolveGeneratedByName(userId, identity)
        val formattedPatientName = formatPatientNameForReport(patient, config.patientNameOption)
        val reportPatient = buildReportPatient(patient, generatedAt, formattedPatientName)

        val metadata = ReportMetadata(
            reportId = reportId,
            session = session,
            patient = reportPatient,
            generatedBy = userId,
            generatedByName = generatedByName,
            generatedAt = generatedAt,
            totalSamples = samples.size,
            totalEggsConfirmed = eggCounts.sumOf { it.count },
            positiveSpecies = lpfPerSpecies.filterValues { it.max > 0 }.keys.sorted(),
            lpfPerSpecies = lpfPerSpecies,
        )

        val pdfDoc = reportPdfBuilder.build(
            metadata = metadata,
            samples = samples,
            detectionsBySample = detectionsBySample,
            config = config,
            generatedByEmail = identity?.email,
            organizationName = deriveOrganizationFromEmail(identity?.email),
        )

        reportPdfRenderer.render(pdfDoc)
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

    private fun resolveGeneratedByName(userId: String, identity: LocalIdentity?): String {
        return identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.email?.takeIf { it.isNotBlank() }
            ?: userId
    }

    private suspend fun buildReportPatient(
        patient: Patient,
        generatedAt: Instant,
        patientName: String,
    ): ReportPatient {
        val barangay = psgcRepository.getBarangay(patient.psgcBarangayCode)
        val barangayLabel = barangay?.fullAddress ?: patient.psgcBarangayCode
        return ReportPatient(
            name = patientName,
            sex = patient.sex,
            ageYears = patient.ageYears(generatedAt),
            barangayLabel = barangayLabel,
        )
    }
}
