package com.agarthavision.domain.usecase.sessions

import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.session.SessionLabelGenerator
import java.time.Instant
import javax.inject.Inject

/**
 * The label to pre-fill the New Session sheet with, for one patient.
 *
 * Reads the patient (for the initials) and that patient's existing labels (for the
 * sequence), then hands both to the pure [SessionLabelGenerator]. After generating a
 * candidate, increments the sequence until the label is not already taken by a
 * manually-edited label, so the suggestion is always fresh and unambiguous. Returns
 * `Result` per C4.
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
    suspend operator fun invoke(
        patientId: String,
        asOf: Instant = Instant.now(),
    ): Result<String> = runCatching {
        val patient = requireNotNull(patientRepository.getPatientById(patientId)) {
            "No patient $patientId; cannot build a smear label for one that is not there."
        }
        var sequence = SessionLabelGenerator.nextSequence(
            sessionRepository.getSessionLabelsForPatient(patientId),
        )
        var candidate = SessionLabelGenerator.generate(patient, sequence, asOf)
        while (sessionRepository.isSessionLabelTaken(patientId, candidate)) {
            sequence++
            candidate = SessionLabelGenerator.generate(patient, sequence, asOf)
        }
        candidate
    }
}
