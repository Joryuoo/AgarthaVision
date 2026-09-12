package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.RecordsTotals
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

        val result = useCase(RecordsQuery(limit = 20)).first()

        assertEquals(1, result.items.size)
        val item = result.items.single()
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

        val item = useCase(RecordsQuery(limit = 20)).first().items.single()
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

        val item = useCase(RecordsQuery(limit = 20)).first().items.single()
        assertEquals(emptyList<String>(), item.speciesLabels)
    }

    // ---------------------------------------------------------------------------
    // Totals mapping
    // ---------------------------------------------------------------------------

    @Test
    fun `totals from repository are surfaced in RecordsResult`() = runTest {
        val sessionRepo = FakeSessionRepository(
            rows = emptyList(),
            totals = RecordsTotals(sessionCount = 7, totalSamples = 42, totalEpg = 13),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        val result = useCase(RecordsQuery(limit = 20)).first()

        assertEquals(7, result.totals.sessionCount)
        assertEquals(42, result.totals.totalSamples)
        assertEquals(13, result.totals.totalEpg)
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

        val result = useCase(RecordsQuery(limit = 20)).first()
        assertEquals(emptyList<SessionRecordItem>(), result.items)
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

        val result = useCase(RecordsQuery(limit = 20)).first()

        assertEquals(2, result.items.size)
        assertEquals(listOf("Ascaris lumbricoides"), result.items[0].speciesLabels)
        assertEquals(listOf("Trichuris trichiura"), result.items[1].speciesLabels)
    }

    // ---------------------------------------------------------------------------
    // Search needle escaping
    // ---------------------------------------------------------------------------

    @Test
    fun `percent sign in search query is escaped before reaching the repository`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "50%", limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals("50\\%", args.query)
    }

    @Test
    fun `underscore in search query is escaped before reaching the repository`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "smear_1", limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals("smear\\_1", args.query)
    }

    @Test
    fun `backslash in search query is escaped before reaching the repository`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "path\\file", limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals("path\\\\file", args.query)
    }

    @Test
    fun `combined special chars percent underscore backslash are all escaped`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "a%b_c\\d", limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals("a\\%b\\_c\\\\d", args.query)
    }

    @Test
    fun `empty search string stays empty after escaping`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "", limit = 20)).first()

        val args = sessionRepo.observePageCallArgs.single()
        assertEquals("", args.query)
    }

    // ---------------------------------------------------------------------------
    // Escaping reaches BOTH page and totals calls symmetrically
    // ---------------------------------------------------------------------------

    @Test
    fun `escaped query is identical for both observeSessionRecordsPage and observeSessionRecordsTotals`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(searchQuery = "a%b_c\\d", limit = 20)).first()

        val pageArgs = sessionRepo.observePageCallArgs.single()
        val totalsArgs = sessionRepo.observeTotalsCallArgs.single()
        assertEquals(
            "escaped query must be the same for page and totals calls",
            pageArgs.query,
            totalsArgs.query,
        )
        assertEquals("a\\%b\\_c\\\\d", pageArgs.query)
    }

    @Test
    fun `startDate and endDate millis are passed identically to observeSessionRecordsTotals`() = runTest {
        val date = LocalDate.of(2024, 6, 15)
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(startDate = date, endDate = date, limit = 20)).first()

        val pageArgs = sessionRepo.observePageCallArgs.single()
        val totalsArgs = sessionRepo.observeTotalsCallArgs.single()
        assertEquals("startMillis must reach totals call", pageArgs.startMillis, totalsArgs.startMillis)
        assertEquals("endMillis must reach totals call", pageArgs.endMillis, totalsArgs.endMillis)
    }

    @Test
    fun `null dates are passed as null to observeSessionRecordsTotals`() = runTest {
        val sessionRepo = FakeSessionRepository(emptyList())
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = sessionRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        useCase(RecordsQuery(startDate = null, endDate = null, limit = 20)).first()

        val totalsArgs = sessionRepo.observeTotalsCallArgs.single()
        assertEquals(null, totalsArgs.startMillis)
        assertEquals(null, totalsArgs.endMillis)
    }

    // ---------------------------------------------------------------------------
    // Multi-emission: combine behaviour of pageFlow and totalsFlow
    // ---------------------------------------------------------------------------

    @Test
    fun `when totals flow emits a second value result re-emits with updated totals and same items`() = runTest {
        val session = session("s1", "u1")
        val row = sessionWithStats(session, totalSamples = 2, totalEpg = 5)
        val totals1 = RecordsTotals(sessionCount = 1, totalSamples = 2, totalEpg = 5)
        val totals2 = RecordsTotals(sessionCount = 2, totalSamples = 4, totalEpg = 10)

        val multiRepo = MultiEmitSessionRepository(
            pageEmissions = listOf(listOf(row)),
            totalsEmissions = listOf(totals1, totals2),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = multiRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        val emissions = useCase(RecordsQuery(limit = 20)).toList()

        assertEquals("should emit twice — once per totals emission", 2, emissions.size)
        // First emission: original totals
        assertEquals(totals1.sessionCount, emissions[0].totals.sessionCount)
        assertEquals(1, emissions[0].items.size)
        // Second emission: updated totals, same items
        assertEquals(totals2.sessionCount, emissions[1].totals.sessionCount)
        assertEquals(1, emissions[1].items.size)
        assertEquals("s1", emissions[1].items.single().session.id)
    }

    @Test
    fun `when page flow emits a second value result re-emits with updated items and preserved totals`() = runTest {
        val session1 = session("s1", "u1")
        val session2 = session("s2", "u1")
        val row1 = sessionWithStats(session1, totalSamples = 1, totalEpg = 2)
        val row2 = sessionWithStats(session2, totalSamples = 3, totalEpg = 6)
        val totals = RecordsTotals(sessionCount = 2, totalSamples = 4, totalEpg = 8)

        val multiRepo = MultiEmitSessionRepository(
            pageEmissions = listOf(listOf(row1), listOf(row1, row2)),
            totalsEmissions = listOf(totals),
        )
        val useCase = GetRecordsUseCase(
            authRepository = FakeAuthRepository(userId = "u1"),
            sessionRepository = multiRepo,
            detectionRepository = FakeDetectionRepository(emptyMap()),
        )

        val emissions = useCase(RecordsQuery(limit = 20)).toList()

        assertEquals("should emit twice — once per page emission", 2, emissions.size)
        // First emission: one item, correct totals
        assertEquals(1, emissions[0].items.size)
        assertEquals(totals.sessionCount, emissions[0].totals.sessionCount)
        // Second emission: two items, same totals preserved
        assertEquals(2, emissions[1].items.size)
        assertEquals(totals.sessionCount, emissions[1].totals.sessionCount)
    }

    @Test
    fun `species needle is NOT escaped even when it contains special chars`() = runTest {
        // Species come from a fixed enum so should pass through as-is
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

data class ObserveTotalsArgs(
    val userId: String,
    val startMillis: Long?,
    val endMillis: Long?,
    val query: String,
    val species: String?,
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
    private val totals: RecordsTotals = RecordsTotals(),
) : SessionRepository {
    val observePageCallArgs = mutableListOf<ObservePageArgs>()
    val observeTotalsCallArgs = mutableListOf<ObserveTotalsArgs>()

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

    override fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> {
        observeTotalsCallArgs += ObserveTotalsArgs(userId, startMillis, endMillis, query, species)
        return flowOf(totals)
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
// Multi-emission fake session repository
// ---------------------------------------------------------------------------

private class MultiEmitSessionRepository(
    private val pageEmissions: List<List<SessionWithStats>>,
    private val totalsEmissions: List<RecordsTotals>,
) : SessionRepository {
    override fun observeAllSessions(userId: String): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun setClaimExempt(sessionId: String, exempt: Boolean) = Unit
    override suspend fun claimSession(sessionId: String, userId: String) = Unit

    override fun observeSessionRecordsPage(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flow {
        pageEmissions.forEach { emit(it) }
    }

    override fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flow {
        totalsEmissions.forEach { emit(it) }
    }
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
