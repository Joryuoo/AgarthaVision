package com.agarthavision.domain.usecase.sessions

import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SessionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GenerateSessionLabelUseCase]. Uses hand-rolled fakes rather than Mockito
 * so the collision loop (which changes output based on repeated `isSessionLabelTaken` calls)
 * is easy to control without capturing argument sequences.
 */
class GenerateSessionLabelUseCaseTest {

    // ---------------------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------------------

    @Test
    fun `returns first candidate when no label is taken`() = runTest {
        val useCase = useCaseFor(
            patient = garcia(),
            existingLabels = emptyList(),
            takenLabels = emptySet(),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        assertEquals("GarciaM-S01", result.getOrThrow())
    }

    @Test
    fun `increments sequence past existing labels`() = runTest {
        // Three smears already exist; the suggestion must be S04.
        val useCase = useCaseFor(
            patient = garcia(),
            existingLabels = listOf("GarciaM-S01", "GarciaM-S02", "GarciaM-S03"),
            takenLabels = emptySet(),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        assertEquals("GarciaM-S04", result.getOrThrow())
    }

    // ---------------------------------------------------------------------------
    // Collision-aware loop
    // ---------------------------------------------------------------------------

    @Test
    fun `skips a candidate that is already taken and returns the next one`() = runTest {
        // Sequence 1 is "taken" (medtech renamed a session to S01 manually). The use case
        // must detect the collision and suggest S02 instead.
        val useCase = useCaseFor(
            patient = garcia(),
            existingLabels = emptyList(),
            takenLabels = setOf("GarciaM-S01"),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        assertEquals("GarciaM-S02", result.getOrThrow())
    }

    @Test
    fun `skips multiple consecutive taken candidates before returning a free one`() = runTest {
        // Both S01 and S02 are taken; the use case must loop past both.
        val useCase = useCaseFor(
            patient = garcia(),
            existingLabels = emptyList(),
            takenLabels = setOf("GarciaM-S01", "GarciaM-S02"),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        assertEquals("GarciaM-S03", result.getOrThrow())
    }

    @Test
    fun `collision check uses the same patientId that the use case was invoked with`() = runTest {
        // Verifies that the label check is scoped to the correct patient. The recording fake
        // captures every (patientId, label) pair passed to isSessionLabelTaken.
        val recording = RecordingSessionRepository(
            patient = garcia(),
            existingLabels = emptyList(),
            takenLabels = emptySet(),
        )
        val useCase = GenerateSessionLabelUseCase(
            patientRepository = FakePatientRepository(garcia()),
            sessionRepository = recording,
        )

        useCase("patient-1")

        assertTrue(
            "isSessionLabelTaken must be called with the same patient id passed to the use case",
            recording.labelChecks.isNotEmpty() &&
                recording.labelChecks.all { (pid, _) -> pid == "patient-1" },
        )
    }

    // ---------------------------------------------------------------------------
    // Error path
    // ---------------------------------------------------------------------------

    @Test
    fun `returns failure when patient is not found`() = runTest {
        val useCase = GenerateSessionLabelUseCase(
            patientRepository = FakePatientRepository(null),
            sessionRepository = NoOpSessionRepository(),
        )

        val result = useCase("missing-patient")

        assertTrue("Must be a failure when the patient does not exist", result.isFailure)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun garcia() = Patient(
        id = "patient-1",
        lastname = "Garcia",
        firstname = "Maria",
        middleName = null,
        sex = Sex.FEMALE,
        birthdate = LocalDate.of(2000, 1, 1),
        psgcBarangayCode = "0730600000",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun useCaseFor(
        patient: Patient,
        existingLabels: List<String>,
        takenLabels: Set<String>,
    ) = GenerateSessionLabelUseCase(
        patientRepository = FakePatientRepository(patient),
        sessionRepository = FakeSessionRepository(
            patientId = patient.id,
            existingLabels = existingLabels,
            takenLabels = takenLabels,
        ),
    )
}

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

private class FakePatientRepository(private val patient: Patient?) : PatientRepository {
    override suspend fun getPatientById(patientId: String): Patient? = patient
    override suspend fun insert(patient: Patient) = Unit
    override suspend fun update(patient: Patient) = Unit
    override fun observePatients(userId: String, query: String, limit: Int): Flow<List<Patient>> = flowOf(emptyList())
    override fun observePatientCount(userId: String, query: String): Flow<Int> = flowOf(0)
    override fun observePatientById(patientId: String): Flow<Patient?> = flowOf(patient)
}

/** A fake that maps taken labels to return true from isSessionLabelTaken. */
private class FakeSessionRepository(
    private val patientId: String,
    private val existingLabels: List<String>,
    private val takenLabels: Set<String>,
) : SessionRepository {
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = takenLabels.contains(label)

    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = existingLabels

    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())
    override fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

/** Records every (patientId, label) pair passed to isSessionLabelTaken. */
private class RecordingSessionRepository(
    private val patient: Patient,
    private val existingLabels: List<String>,
    private val takenLabels: Set<String>,
) : SessionRepository {
    val labelChecks = mutableListOf<Pair<String, String>>()

    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean {
        labelChecks += Pair(patientId, label)
        return takenLabels.contains(label)
    }

    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = existingLabels

    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())
    override fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

private class NoOpSessionRepository : SessionRepository {
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())
    override fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}
