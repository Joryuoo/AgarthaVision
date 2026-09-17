package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DailyEggCount
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSessionSamplesUseCaseTest {

    // ─────────────────────────── signed-out + unowned ────────────────────────

    @Test
    fun `signed-out with unowned session returns Visible with both samples and their detections`() =
        runTest {
            val sample1 = sessionsSample(id = "s1", sessionId = "session-x", userId = null)
            val sample2 = sessionsSample(id = "s2", sessionId = "session-x", userId = null)
            val det1 = sessionsDetection(id = "d1", sampleId = "s1")
            val det2 = sessionsDetection(id = "d2", sampleId = "s2")
            val session = sessionsSession(id = "session-x", userId = null)

            val useCase = GetSessionSamplesUseCase(
                authRepository = SamplesAuthRepository(localUserId = null, liveUserId = null),
                sessionRepository = SamplesSessionRepository(session),
                sampleRepository = SamplesSampleRepository(
                    samplesBySession = mapOf("session-x" to listOf(sample1, sample2)),
                ),
                detectionRepository = SamplesDetectionRepository(
                    detectionsBySample = mapOf("s1" to listOf(det1), "s2" to listOf(det2)),
                ),
            )

            val result = useCase("session-x").first()

            assertTrue(result is SessionSamplesResult.Visible)
            val data = (result as SessionSamplesResult.Visible).data
            assertEquals(2, data.samples.size)
            assertEquals(setOf("s1", "s2"), data.samples.map { it.sample.id }.toSet())
            assertEquals(listOf(det1), data.samples.first { it.sample.id == "s1" }.detections)
            assertEquals(listOf(det2), data.samples.first { it.sample.id == "s2" }.detections)
        }

    // ─────────────────────────── ownership mismatch → NotVisible ─────────────

    @Test
    fun `session owned by user-a returns NotVisible when identity is user-b`() = runTest {
        val useCase = GetSessionSamplesUseCase(
            authRepository = SamplesAuthRepository(localUserId = "user-b", liveUserId = "user-b"),
            sessionRepository = SamplesSessionRepository(sessionsSession(id = "s", userId = "user-a")),
            sampleRepository = SamplesSampleRepository(emptyMap()),
            detectionRepository = SamplesDetectionRepository(emptyMap()),
        )

        assertEquals(SessionSamplesResult.NotVisible, useCase("s").first())
    }

    // ─────────────────────────── missing session → NotFound ──────────────────

    @Test
    fun `missing session returns NotFound`() = runTest {
        val useCase = GetSessionSamplesUseCase(
            authRepository = SamplesAuthRepository(localUserId = "user-a", liveUserId = "user-a"),
            sessionRepository = SamplesSessionRepository(null),
            sampleRepository = SamplesSampleRepository(emptyMap()),
            detectionRepository = SamplesDetectionRepository(emptyMap()),
        )

        assertEquals(SessionSamplesResult.NotFound, useCase("missing").first())
    }

    // ─────────────────────────── owned session, matching identity → Visible ──

    @Test
    fun `session owned by user-a returns Visible when identity is user-a`() = runTest {
        val sample = sessionsSample(id = "s1", sessionId = "session-1", userId = "user-a")
        val detection = sessionsDetection(id = "d1", sampleId = "s1")
        val session = sessionsSession(id = "session-1", userId = "user-a")

        val useCase = GetSessionSamplesUseCase(
            authRepository = SamplesAuthRepository(localUserId = "user-a", liveUserId = "user-a"),
            sessionRepository = SamplesSessionRepository(session),
            sampleRepository = SamplesSampleRepository(
                samplesBySession = mapOf("session-1" to listOf(sample)),
            ),
            detectionRepository = SamplesDetectionRepository(
                detectionsBySample = mapOf("s1" to listOf(detection)),
            ),
        )

        val result = useCase("session-1").first()

        assertTrue(result is SessionSamplesResult.Visible)
        val data = (result as SessionSamplesResult.Visible).data
        assertEquals("session-1", data.session.id)
        assertEquals(1, data.samples.size)
    }

    // ─────────────────── offline-signed-in: local cached id ≠ live id ────────

    /**
     * Pins bug: before this fix, the use case called getCurrentUserId() (null when offline)
     * and incorrectly blocked access to the medtech's own session.
     * currentLocalUserId() is read from cache and stays non-null while offline.
     */
    @Test
    fun `offline-signed-in with owned session returns Visible using cached identity`() = runTest {
        val sample = sessionsSample(id = "s1", sessionId = "session-1", userId = "user-a")
        val detection = sessionsDetection(id = "d1", sampleId = "s1")
        val session = sessionsSession(id = "session-1", userId = "user-a")

        val useCase = GetSessionSamplesUseCase(
            // liveUserId = null simulates no network / expired token; local cache has "user-a"
            authRepository = SamplesAuthRepository(localUserId = "user-a", liveUserId = null),
            sessionRepository = SamplesSessionRepository(session),
            sampleRepository = SamplesSampleRepository(
                samplesBySession = mapOf("session-1" to listOf(sample)),
            ),
            detectionRepository = SamplesDetectionRepository(
                detectionsBySample = mapOf("s1" to listOf(detection)),
            ),
        )

        val result = useCase("session-1").first()

        assertTrue(
            "Expected Visible but got $result — use case must use currentLocalUserId(), not getCurrentUserId()",
            result is SessionSamplesResult.Visible,
        )
    }

    // ─────────────────── signed-in + unowned session → Visible ───────────────

    @Test
    fun `signed-in user can see unowned session`() = runTest {
        val sample = sessionsSample(id = "s1", sessionId = "session-u", userId = null)
        val detection = sessionsDetection(id = "d1", sampleId = "s1")
        val session = sessionsSession(id = "session-u", userId = null)

        val useCase = GetSessionSamplesUseCase(
            authRepository = SamplesAuthRepository(localUserId = "user-a", liveUserId = "user-a"),
            sessionRepository = SamplesSessionRepository(session),
            sampleRepository = SamplesSampleRepository(
                samplesBySession = mapOf("session-u" to listOf(sample)),
            ),
            detectionRepository = SamplesDetectionRepository(
                detectionsBySample = mapOf("s1" to listOf(detection)),
            ),
        )

        val result = useCase("session-u").first()

        assertTrue(result is SessionSamplesResult.Visible)
    }
}

// ─────────────────────────────── fakes ───────────────────────────────────────

/**
 * Auth fake that lets tests set the cached local identity independently from the
 * "live" identity so offline scenarios can be modelled precisely.
 */
private class SamplesAuthRepository(
    private val localUserId: String?,
    private val liveUserId: String?,
) : AuthRepository {
    override fun observeLocalIdentity(): Flow<LocalIdentity?> =
        flowOf(localUserId?.let { LocalIdentity(userId = it, email = "user@example.com") })

    override suspend fun currentLocalUserId(): String? = localUserId
    override suspend fun getCurrentUserId(): String? = liveUserId
    override suspend fun isAuthenticated(): Boolean = liveUserId != null
    override suspend fun hasActiveSession(): Boolean = liveUserId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signOut() = Unit
}

private class SamplesSessionRepository(
    private val session: Session?,
) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = session?.takeIf { it.id == sessionId }
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

private class SamplesSampleRepository(
    private val samplesBySession: Map<String, List<Sample>>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(emptyList())
    override suspend fun getSampleById(sampleId: String): Sample? = null
    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(samplesBySession[sessionId].orEmpty())
    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
        samplesBySession[sessionId].orEmpty()
    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()
    override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(emptyList())
}

private class SamplesDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()
    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())
    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?) =
        emptyList<EggCount>()
    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        flowOf(emptyList())
    override fun observeDailyEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<DailyEggCount>> =
        flowOf(emptyList())
    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}

private fun sessionsSession(id: String, userId: String?) = Session(
    id = id,
    userId = userId,
    deviceId = "device-1",
    startedAt = 1_000L,
    patientId = "patient-1",
    label = null,
)

private fun sessionsSample(id: String, sessionId: String, userId: String?) = Sample(
    id = id,
    userId = userId,
    timestamp = 1_500L,
    verifiedAt = 1_600L,
    deviceId = "device-1",
    sessionId = sessionId,
    filePath = "/tmp/$id.jpg",
    storagePath = userId?.let { "$it/$id.jpg" },
    status = SampleStatus.SYNCED,
)

private fun sessionsDetection(id: String, sampleId: String) = Detection(
    id = id,
    sampleId = sampleId,
    classLabel = "Ascaris",
    confidence = 0.9f,
    bboxX = 0.1f,
    bboxY = 0.2f,
    bboxW = 0.3f,
    bboxH = 0.4f,
    verdict = DetectionVerdict.CONFIRMED,
    expertClass = null,
    verifiedByUser = true,
)
