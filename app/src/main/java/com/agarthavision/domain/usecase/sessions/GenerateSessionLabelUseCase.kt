package com.agarthavision.domain.usecase.sessions

import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.session.SessionLabelGenerator
import javax.inject.Inject

/**
 * The label to pre-fill the New Session sheet with, for one patient.
 *
 * Reads the patient (for the initials and the barangay code) and that patient's existing
 * labels (for the sequence), then hands both to the pure
 * [SessionLabelGenerator]. Returns `Result` per C4.
 *
 * A failure here is not fatal to creating a session: the sheet falls back to an empty field
 * the medtech types into, which is exactly what it did before this ticket. That is why the
 * missing patient is a failure rather than a blank label — a blank one looks like a
 * suggestion that happens to be empty, and nobody would notice the patient had not loaded.
 */
class GenerateSessionLabelUseCase @Inject constructor(
    private val patientRepository: PatientRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(patientId: String): Result<String> = runCatching {
        val patient = requireNotNull(patientRepository.getPatientById(patientId)) {
            "No patient $patientId; cannot build a smear label for one that is not there."
        }
        val sequence = SessionLabelGenerator.nextSequence(
            sessionRepository.getSessionLabelsForPatient(patientId),
        )
        SessionLabelGenerator.generate(patient, sequence)
    }
}
