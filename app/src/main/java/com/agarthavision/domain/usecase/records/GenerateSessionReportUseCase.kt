package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPatient
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.reports.aggregateLpfPerSpecies
import com.agarthavision.domain.sync.SyncScheduler
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Generates a persisted session report and writes the patient-facing PDF to device storage.
 */
// Composition-root use case wiring 12 distinct, non-overlapping DI dependencies (repositories,
// file store, PDF builder + renderer, sync use case); each is independently meaningful and
// bundling would not simplify the real dependency graph.
@Suppress("LongParameterList")
class GenerateSessionReportUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val findingDao: SampleSpeciesFindingDao,
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val reportRepository: ReportRepository,
    private val reportFileStore: ReportFileStore,
    private val reportPdfBuilder: ReportPdfBuilder,
    private val reportPdfRenderer: ReportPdfRenderer,
    private val syncReportUseCase: SyncReportUseCase,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(sessionId: String): Result<Report> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val session = requireNotNull(sessionRepository.getSessionById(sessionId)) {
            "Session $sessionId does not exist."
        }
        require(session.userId == userId) {
            "Session $sessionId is not owned by the current user."
        }

        // Verified samples only: the query leaves out every frame still waiting in the queue.
        // With none, the report would be a signed-off document covering zero fields, written,
        // uploaded and synced before anyone noticed it was empty (86d4bzm9k). The button is
        // disabled in that state too; this keeps any other caller from producing one.
        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        require(samples.isNotEmpty()) { NO_VERIFIED_SAMPLES_MESSAGE }

        val patient = requireNotNull(patientRepository.getPatientById(session.patientId)) {
            PATIENT_NOT_ON_DEVICE_MESSAGE
        }

        // No floor. It existed to keep a mean from dividing by zero, and the mean is gone.
        val fieldCount = samples.size
        val detectionsBySample = samples.associate { sample ->
            sample.id to detectionRepository.getDetectionsForSample(sample.id)
        }

        val eggCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val findings = findingDao.getFindingsForSession(sessionId, userId)
        // The shared aggregation (PB-17). This file used to carry its own copy, so the report
        // a medtech hands a patient and the screen they read it off could disagree about the
        // same session with neither being obviously wrong.
        val lpfPerSpecies = aggregateLpfPerSpecies(findings, fieldCount)

        val reportId = UUID.randomUUID().toString()
        // One instant for both the report's timestamp and the patient's age, so a report never
        // prints an age computed a beat apart from the "Generated at" line beside it.
        val generatedAt = Instant.now()
        val generatedByName = resolveGeneratedByName(userId)
        val reportPatient = buildReportPatient(patient, generatedAt)

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

        val pdfDoc = reportPdfBuilder.build(metadata, samples, detectionsBySample)
        val pdfBytes = reportPdfRenderer.render(pdfDoc)
        val pdfPath = reportFileStore.writePdf(reportId, sessionId, pdfBytes)

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
            csvFilePath = null,
            pdfFilePath = pdfPath,
            supabaseStatus = ReportSyncStatus.PENDING,
        )
        reportRepository.insert(report)
        // Direct push first, as before. The scheduler is the fallback for when it does not
        // land: a report left PENDING otherwise waits for someone to open Settings.
        syncReportUseCase(reportId)
        syncScheduler.requestSync()
        report
    }

    /**
     * The medtech's name as printed on the report: display name first, falling back to email,
     * then the raw user id — mirroring the convention used wherever a medtech's identity is
     * shown without a guaranteed display name.
     */
    private suspend fun resolveGeneratedByName(userId: String): String {
        val identity = authRepository.observeLocalIdentity().first()
        return identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.email?.takeIf { it.isNotBlank() }
            ?: userId
    }

    private suspend fun buildReportPatient(
        patient: Patient,
        generatedAt: Instant,
    ): ReportPatient {
        val barangay = psgcRepository.getBarangay(patient.psgcBarangayCode)
        val barangayLabel = barangay?.let { "${it.name} · ${it.parentPath}" }
            ?: patient.psgcBarangayCode
        return ReportPatient(
            name = patient.displayName,
            sex = patient.sex,
            ageYears = patient.ageYears(generatedAt),
            barangayLabel = barangayLabel,
        )
    }

    companion object {
        const val NO_VERIFIED_SAMPLES_MESSAGE = "Verify at least one sample to generate a report."
        const val PATIENT_NOT_ON_DEVICE_MESSAGE =
            "This session's patient is not on this device yet. Sync, then try again."
    }
}
