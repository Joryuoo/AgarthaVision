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
    /** Live fields that are still in the verification queue for this session. */
    val unverifiedSampleCount: Int = 0,
)

/**
 * Lists a patient's sessions with their live verified and unverified field counts, so the
 * report sheet can disable sessions without verified fields and warn about excluded fields.
 */
class GetPatientReportCandidatesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
) {
    suspend operator fun invoke(
        patientId: String,
        sessionIds: Set<String>? = null,
    ): Result<List<PatientReportCandidate>> = runCatching {
        val userId = requireNotNull(authRepository.currentLocalUserId()) {
            "Sign in to generate reports."
        }
        val sessions = sessionRepository.getSessionsForPatient(patientId, userId)
        val selectedSessions = if (sessionIds == null) sessions else sessions.filter { it.id in sessionIds }
        selectedSessions.map { session ->
            val verifiedSampleCount = sampleRepository.getSamplesForSession(session.id, userId).size
            val unverifiedSampleCount = sampleRepository.countUnverifiedFieldsForReport(session.id, userId)
            PatientReportCandidate(
                session = session,
                verifiedSampleCount = verifiedSampleCount,
                unverifiedSampleCount = unverifiedSampleCount,
            )
        }
    }
}
