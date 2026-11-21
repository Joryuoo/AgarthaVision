package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [GetPatientReportCandidatesUseCase]. */
class GetPatientReportCandidatesUseCaseTest {

    private fun session(id: String) = Session(
        id = id,
        userId = "user-1",
        deviceId = "device-1",
        startedAt = 1_000L,
        patientId = "patient-1",
        label = id,
    )

    private fun sample(id: String, sessionId: String) = Sample(
        id = id,
        userId = "user-1",
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = sessionId,
        filePath = "/tmp/$id.jpg",
        storagePath = "user-1/$id.jpg",
        inferenceModelVersion = "model-1",
        isManual = false,
        status = SampleStatus.SYNCED,
    )

    @Test
    fun `annotates every session with both field counts, including zero`() = runTest {
        val sessions = listOf(session("s1"), session("s2"))
        val samples = listOf(
            sample("sample-1", "s1"),
            sample("sample-2", "s1").copy(status = SampleStatus.FLAGGED),
            sample("sample-3", "s1").copy(status = SampleStatus.FLAGGED),
        )
        val useCase = GetPatientReportCandidatesUseCase(
            authRepository = FakeAuth("user-1"),
            sessionRepository = FakeSessions(sessions),
            sampleRepository = FakeSamples(samples),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val candidates = result.getOrThrow()
        assertEquals(2, candidates.size)
        assertEquals(1, candidates.first { it.session.id == "s1" }.verifiedSampleCount)
        assertEquals(2, candidates.first { it.session.id == "s1" }.unverifiedSampleCount)
        assertEquals(0, candidates.first { it.session.id == "s2" }.verifiedSampleCount)
        assertEquals(0, candidates.first { it.session.id == "s2" }.unverifiedSampleCount)
    }

    @Test
    fun `fails when no user is authenticated`() = runTest {
        val useCase = GetPatientReportCandidatesUseCase(
            authRepository = FakeAuth(null),
            sessionRepository = FakeSessions(emptyList()),
            sampleRepository = FakeSamples(emptyList()),
        )

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `a partial refresh reads counts only for the requested sessions`() = runTest {
        val samples = FakeSamples(emptyList())
        val useCase = GetPatientReportCandidatesUseCase(
            authRepository = FakeAuth("user-1"),
            sessionRepository = FakeSessions(listOf(session("s1"), session("s2"))),
            sampleRepository = samples,
        )

        val refreshed = useCase("patient-1", sessionIds = setOf("s2")).getOrThrow()
        val empty = useCase("patient-1", sessionIds = emptySet()).getOrThrow()

        assertEquals(listOf("s2"), refreshed.map { it.session.id })
        assertEquals(listOf("s2"), samples.observedFlaggedSessions)
        assertTrue(empty.isEmpty())
    }

    private class FakeAuth(private val userId: String?) : AuthRepository {
        override fun observeLocalIdentity(): Flow<LocalIdentity?> = flowOf(null)
        override suspend fun currentLocalUserId(): String? = userId
        override suspend fun isAuthenticated(): Boolean = userId != null
        override suspend fun signIn(email: String, password: String, humanCheckToken: String?) = Unit
        override suspend fun hasActiveSession(): Boolean = userId != null
        override suspend fun getCurrentUserId(): String? = userId
        override suspend fun signOut() = Unit
        override suspend fun changePassword(currentPassword: String, newPassword: String, humanCheckToken: String?) =
            PasswordChangeResult.Failed
    }

    private class FakeSessions(private val sessions: List<Session>) : SessionRepository {
        override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(sessions)
        override suspend fun getSessionById(sessionId: String): Session? = sessions.firstOrNull { it.id == sessionId }
        override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
        override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
        override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(sessions)
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
        override suspend fun isSessionLabelTaken(
            patientId: String,
            label: String,
            excludingSessionId: String?,
        ): Boolean = false
        override suspend fun getSessionsForPatient(patientId: String, userId: String): List<Session> = sessions
    }

    private class FakeSamples(private val samples: List<Sample>) : SampleRepository {
        val observedFlaggedSessions = mutableListOf<String>()

        override suspend fun saveSample(sample: Sample) = Unit
        override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
        override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples)
        override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
        override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
            flowOf(getSessionSamples(sessionId))
        override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
            getSessionSamples(sessionId)
        override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()
        override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> {
            observedFlaggedSessions += sessionId
            return flowOf(samples.filter { it.sessionId == sessionId && it.status == SampleStatus.FLAGGED })
        }

        private fun getSessionSamples(sessionId: String): List<Sample> =
            samples.filter { it.sessionId == sessionId && it.status != SampleStatus.FLAGGED }
    }
}
