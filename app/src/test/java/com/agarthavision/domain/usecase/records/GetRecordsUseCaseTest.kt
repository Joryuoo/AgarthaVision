package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetRecordsUseCaseTest {
    @Test
    fun `records are scoped to current user and summarized by session`() = runTest {
        val session = session(id = "session-1", userId = "user-1")
        val sample = sample(id = "sample-1", sessionId = "session-1", userId = "user-1")
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "user-1"),
            sessionRepository = FakeSessionRepository(listOf(session)),
            sampleRepository = FakeSampleRepository(mapOf("session-1" to listOf(sample))),
            detectionRepository = FakeDetectionRepository(
                mapOf(
                    "sample-1" to listOf(
                        detection(sampleId = "sample-1", classLabel = "Ascaris", confidence = 0.91f),
                    ),
                ),
            ),
        )

        val records = useCase().first()

        assertEquals(1, records.size)
        assertEquals("session-1", records.single().session.id)
        assertEquals(1, records.single().sampleCount)
        assertEquals(listOf("Ascaris"), records.single().speciesLabels)
        assertEquals(10.0, records.single().latitude)
        assertEquals(20.0, records.single().longitude)
    }

    @Test
    fun `records are empty when no user is authenticated`() = runTest {
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = null),
            sessionRepository = FakeSessionRepository(listOf(session(id = "session-1", userId = "user-1"))),
            sampleRepository = FakeSampleRepository(emptyMap()),
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        assertEquals(emptyList<SessionRecordItem>(), useCase().first())
    }
}

private class FakeAuthRepository(private val userId: String?) : AuthRepository {
    override val userIdFlow: Flow<String?> = flowOf(userId)
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
}

private class FakeSessionRepository(
    private val sessions: List<Session>,
) : SessionRepository {
    override fun observeAllSessions(userId: String): Flow<List<Session>> =
        flowOf(sessions.filter { it.userId == userId })

    override suspend fun getSessionById(sessionId: String): Session? =
        sessions.firstOrNull { it.id == sessionId }
}

private class FakeSampleRepository(
    private val samplesBySession: Map<String, List<Sample>>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> =
        flowOf(samplesBySession.values.flatten().filter { it.userId == userId })

    override suspend fun getSampleById(sampleId: String): Sample? =
        samplesBySession.values.flatten().firstOrNull { it.id == sampleId }

    override fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>> =
        flowOf(samplesBySession[sessionId].orEmpty().filter { it.userId == userId })

    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> =
        samplesBySession[sessionId].orEmpty().filter { it.userId == userId }

    override suspend fun getSamplesPendingSync(userId: String): List<Sample> = emptyList()
}

private class FakeDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String) = emptyList<EggCount>()
}

private fun session(id: String, userId: String): Session =
    Session(
        id = id,
        userId = userId,
        deviceId = "device-1",
        startedAt = 1_000L,
        endedAt = 2_000L,
        notes = null,
        label = null,
    )

private fun sample(id: String, sessionId: String, userId: String): Sample =
    Sample(
        id = id,
        userId = userId,
        timestamp = 1_500L,
        verifiedAt = 1_600L,
        deviceId = "device-1",
        sessionId = sessionId,
        filePath = "/tmp/$id.jpg",
        storagePath = "$userId/$id.jpg",
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        status = SampleStatus.SYNCED,
    )

private fun detection(sampleId: String, classLabel: String, confidence: Float): Detection =
    Detection(
        id = "detection-$sampleId",
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = 0.1f,
        bboxY = 0.2f,
        bboxW = 0.3f,
        bboxH = 0.4f,
        verdict = DetectionVerdict.CONFIRMED,
        expertClass = null,
        verifiedByUser = true,
    )
