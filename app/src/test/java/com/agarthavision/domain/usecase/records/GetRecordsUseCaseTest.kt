package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DailyEggCount
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GetRecordsUseCaseTest {

    // ---------------------------------------------------------------------------
    // Happy path: correct mapping
    // ---------------------------------------------------------------------------

    @Test
    fun `records map sampleCount totalEpg and speciesLabels from bulk fetch`() = runTest {
        val session = session(id = "session-1", userId = "user-1")
        val row = sessionWithStats(session, totalSamples = 3, totalEpg = 42)
        val sessionRepo = FakeSessionRepository(listOf(row))
        val detectionRepo = FakeDetectionRepository(
            speciesMap = mapOf("session-1" to listOf("Ascaris lumbricoides", "Trichuris trichiura")),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "user-1"),
            sessionRepository = sessionRepo,
            detectionRepository = detectionRepo,
        )

        val records = useCase(RecordsQuery(limit = 20)).first()

        assertEquals(1, records.size)
        val item = records.single()
        assertEquals("session-1", item.session.id)
        assertEquals(3, item.sampleCount)
        assertEquals(42, item.totalEpg)
        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura"), item.speciesLabels)
    }

    @Test
    fun `speciesLabels come straight from the map keyed by session id`() = runTest {
        val session = session(id = "s1", userId = "u1")
        val row = sessionWithStats(session, totalSamples = 1, totalEpg = 0)
        val detectionRepo = FakeDetectionRepository(
            speciesMap = mapOf("s1" to listOf("Hookworm", "Ascaris lumbricoides")),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = FakeSessionRepository(listOf(row)),
            detectionRepository = detectionRepo,
        )

        val item = useCase(RecordsQuery(limit = 20)).first().single()
        assertEquals(listOf("Hookworm", "Ascaris lumbricoides"), item.speciesLabels)
    }

    @Test
    fun `speciesLabels are empty when session has no detections in bulk map`() = runTest {
        val session = session(id = "no-detections", userId = "u1")
        val row = sessionWithStats(session, totalSamples = 0, totalEpg = 0)
        val detectionRepo = FakeDetectionRepository(speciesMap = emptyMap())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = FakeSessionRepository(listOf(row)),
            detectionRepository = detectionRepo,
        )

        val item = useCase(RecordsQuery(limit = 20)).first().single()
        assertEquals(emptyList<String>(), item.speciesLabels)
    }

    // ---------------------------------------------------------------------------
    // Null user path
    // ---------------------------------------------------------------------------

    @Test
    fun `records are empty when no user is authenticated`() = runTest {
        val sessionRepo = FakeSessionRepository(
            listOf(sessionWithStats(session("session-1", "user-1"), totalSamples = 1, totalEpg = 0)),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = null),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        val records = useCase(RecordsQuery(limit = 20)).first()
        assertEquals(emptyList<SessionRecordItem>(), records)
        // observeSessionRecordsPage must NOT have been called for a null user
        assertTrue(sessionRepo.observePageCallArgs.isEmpty())
    }

    // ---------------------------------------------------------------------------
    // Query args pass-through
    // ---------------------------------------------------------------------------

    @Test
    fun `limit is passed through to observeSessionRecordsPage`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(limit = 50)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals(50, args.limit)
    }

    @Test
    fun `species EggSpecies maps to its canonicalClass string`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(species = EggSpecies.ASCARIS, limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals(EggSpecies.ASCARIS.canonicalClass, args.species)
    }

    @Test
    fun `null species maps to null needle`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(species = null, limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals(null, args.species)
    }

    @Test
    fun `startDate converts to atStartOfDay epoch millis`() = runTest {
        val date = LocalDate.of(2024, 3, 15)
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(startDate = date, limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        val expected = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, args.startMillis)
    }

    @Test
    fun `endDate converts to inclusive end-of-day epoch millis`() = runTest {
        val date = LocalDate.of(2024, 3, 15)
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(endDate = date, limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        // endDate: plusDays(1).atStartOfDay - 1ms
        val expected = date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().minusMillis(1).toEpochMilli()
        assertEquals(expected, args.endMillis)
    }

    @Test
    fun `null startDate and endDate pass null millis to repository`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(startDate = null, endDate = null, limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals(null, args.startMillis)
        assertEquals(null, args.endMillis)
    }

    @Test
    fun `multiple sessions each receive labels from the bulk map`() = runTest {
        val s1 = session("s1", "u1")
        val s2 = session("s2", "u1")
        val rows = listOf(
            sessionWithStats(s1, 2, 10),
            sessionWithStats(s2, 1, 5),
        )
        val detectionRepo = FakeDetectionRepository(
            speciesMap = mapOf(
                "s1" to listOf("Ascaris lumbricoides"),
                "s2" to listOf("Trichuris trichiura"),
            ),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = FakeSessionRepository(rows),
            detectionRepository = detectionRepo,
        )

        val records = useCase(RecordsQuery(limit = 20)).first()

        assertEquals(2, records.size)
        assertEquals(listOf("Ascaris lumbricoides"), records[0].speciesLabels)
        assertEquals(listOf("Trichuris trichiura"), records[1].speciesLabels)
    }
}

// ---------------------------------------------------------------------------
// Captured args
// ---------------------------------------------------------------------------

data class ObservePageArgs(
    val userId: String,
    val startMillis: Long?,
    val endMillis: Long?,
    val query: String,
    val species: String?,
    val limit: Int,
)

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

internal class FakeAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeLocalIdentity(): Flow<com.agarthavision.domain.model.LocalIdentity?> =
        flowOf(userId?.let { com.agarthavision.domain.model.LocalIdentity(userId = it, email = "user@example.com") })
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
}

internal class FakeSessionRepository(
    private val rows: List<SessionWithStats> = emptyList(),
) : SessionRepository {
    val observePageCallArgs = mutableListOf<ObservePageArgs>()

    override fun observeAllSessions(userId: String): Flow<List<Session>> =
        flowOf(rows.map { it.session }.filter { it.userId == userId })

    override suspend fun getSessionById(sessionId: String): Session? =
        rows.map { it.session }.firstOrNull { it.id == sessionId }

    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())

    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> =
        flowOf(rows.map { it.session }.filter { it.userId == userId })
    override suspend fun setClaimExempt(sessionId: String, exempt: Boolean) = Unit
    override suspend fun claimSession(sessionId: String, userId: String) = Unit

    override fun observeSessionRecordsPage(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> {
        observePageCallArgs += ObservePageArgs(userId, startMillis, endMillis, query, species, limit)
        return flowOf(rows.take(limit))
    }
}

internal class FakeDetectionRepository(
    private val speciesMap: Map<String, List<String>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> = emptyList()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(emptyList())

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String) =
        emptyList<EggCount>()

    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        flowOf(emptyList())

    override fun observeDailyEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<DailyEggCount>> =
        flowOf(emptyList())

    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        speciesMap.filterKeys { it in sessionIds }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

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

private fun sessionWithStats(session: Session, totalSamples: Int, totalEpg: Int): SessionWithStats =
    SessionWithStats(
        session = session,
        totalSamples = totalSamples,
        verifiedSamples = 0,
        unverifiedSamples = 0,
        totalEpg = totalEpg,
    )
