package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import javax.inject.Inject

/** One session offered in the "generate patient report" session picker. */
data class PatientReportCandidate(
    val session: Session,
    /** Live verified samples in this session — the count the report generation would use. */
    val verifiedSampleCount: Int,
)

/**
 * Lists a patient's sessions, each annotated with its verified-sample count, so the "generate
 * report" sheet can show every session and disable the ones with none (D6).
 */
class GetPatientReportCandidatesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
) {
    suspend operator fun invoke(patientId: String): Result<List<PatientReportCandidate>> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val sessions = sessionRepository.getSessionsForPatient(patientId, userId)
        sessions.map { session ->
            val verifiedSampleCount = sampleRepository.getSamplesForSession(session.id, userId).size
            PatientReportCandidate(session = session, verifiedSampleCount = verifiedSampleCount)
        }
    }
}
