package com.agarthavision.ui.records

import app.cash.turbine.test
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.FakeAuthRepository
import com.agarthavision.domain.usecase.records.FakeDetectionRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    companion object {
        private const val PAGE_SIZE = 20
    }

    // ---------------------------------------------------------------------------
    // Happy path: first emission
    // ---------------------------------------------------------------------------

    @Test
    fun `first emission sets isLoading false with sessions from use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val session = makeSession("s1", "u1")
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> listOf(sessionWithStats(session)).take(limit) },
            )

            vm.state.test {
                // Advance past the 300 ms debounce so the settled state is emitted.
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(1, settled.sessions.size)
                assertEquals("s1", settled.sessions.single().session.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // CORE PART-B REGRESSION GUARD: stateIn WhileSubscribed retains last value
    // after the upstream is cancelled (past the 5-second stop timeout).
    // ---------------------------------------------------------------------------

    @Test
    fun `stateIn WhileSubscribed retains last value after upstream cancellation`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val session = makeSession("s1", "u1")
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> listOf(sessionWithStats(session)).take(limit) },
            )

            // First collection — let the VM settle to a non-loading state.
            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(1, settled.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }

            // Advance past the 5-second WhileSubscribed stop timeout so the upstream
            // Room query is actually cancelled, but stateIn's replay cache is preserved.
            advanceTimeBy(6_000)

            // Second collection — stateIn must immediately replay the last non-loading state.
            // A fresh RecordsState() (isLoading=true) would indicate the cache was reset.
            vm.state.test {
                val resubscribed = awaitItem()
                assertFalse(
                    "stateIn replay should return last non-loading state, not the initial default",
                    resubscribed.isLoading,
                )
                assertEquals(
                    "stateIn replay should return the last session list, not an empty one",
                    1,
                    resubscribed.sessions.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Pagination: onLoadMore grows sessions; canLoadMore reflects size vs limit
    // ---------------------------------------------------------------------------

    @Test
    fun `canLoadMore is true when returned size equals limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Return exactly PAGE_SIZE sessions so size == limit
            val rows = (1..PAGE_SIZE).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(PAGE_SIZE, settled.sessions.size)
                assertTrue("canLoadMore should be true when size == limit", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canLoadMore is false when returned size is less than limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Return only 5 sessions, less than PAGE_SIZE
            val rows = (1..5).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(5, settled.sessions.size)
                assertFalse("canLoadMore should be false when size < limit", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onLoadMore increases limit and grows session list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 5)).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                // Wait for the initial settled state
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.sessions.size)

                // Trigger load-more and let the pipeline re-execute
                vm.onLoadMore()
                advanceUntilIdle()

                val afterLoadMore = expectMostRecentItem()
                assertEquals(PAGE_SIZE + 5, afterLoadMore.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Filter setters reset limit
    // ---------------------------------------------------------------------------

    @Test
    fun `onSpeciesSelected resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.sessions.size)

                // Expand limit
                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem() // PAGE_SIZE + 10 items

                // Filter resets limit → back to PAGE_SIZE results
                vm.onSpeciesSelected(EggSpecies.ASCARIS)
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDateRangeSelected resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onDateRangeSelected(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31))
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onSearchChanged resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSearchChanged("smear")
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Debounce: fast typing coalesces into a single repo call
    // ---------------------------------------------------------------------------

    @Test
    fun `typing fast - searchQuery in state reflects raw input before debounce and repo called only once`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository(rowsByLimit = { emptyList() })
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                // Settle the initial load (debounce elapses for the empty query).
                advanceUntilIdle()
                expectMostRecentItem() // isLoading=false, searchQuery=""

                val baseCallCount = recording.capturedQueries.size

                // Three rapid changes inside the 300 ms debounce window.
                vm.onSearchChanged("a")
                vm.onSearchChanged("ab")
                vm.onSearchChanged("abc")

                // Advance less than the 300 ms debounce so raw searchQuery has propagated
                // through the combine but the debounced flow has not yet re-emitted.
                advanceTimeBy(250)

                // state.searchQuery must reflect "abc" immediately (from raw flow in combine).
                val midState = expectMostRecentItem()
                assertEquals(
                    "state.searchQuery should reflect the latest raw input before debounce elapses",
                    "abc",
                    midState.searchQuery,
                )
                // Debounce hasn't fired yet — no new repo call.
                assertEquals(
                    "repo should not have been called again before debounce elapses",
                    baseCallCount,
                    recording.capturedQueries.size,
                )

                // Now let the debounce fire.
                advanceUntilIdle()

                val newCalls = recording.capturedQueries.drop(baseCallCount)
                assertEquals(
                    "debounce should coalesce rapid changes into exactly one repo call",
                    1,
                    newCalls.size,
                )
                assertEquals(
                    "the single repo call should use the last typed value",
                    "abc",
                    newCalls.single(),
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // onSearchChanged with unchanged text — documents actual behaviour
    // ---------------------------------------------------------------------------

    @Test
    fun `onSearchChanged same text resets limit even when search needle is unchanged`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Enough rows to fill PAGE_SIZE + extra pages.
            val rows = (1..(PAGE_SIZE + 10)).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem() // 20 sessions

                vm.onLoadMore() // limit → 40
                advanceUntilIdle()
                val expanded = expectMostRecentItem()
                assertEquals(PAGE_SIZE + 10, expanded.sessions.size)

                // Call onSearchChanged with the SAME text that's already in the field ("").
                // Even though the search needle is unchanged (no new debounce emission),
                // limit MUST be reset to PAGE_SIZE because onSearchChanged always resets it.
                vm.onSearchChanged("")
                advanceUntilIdle()

                val afterSameText = expectMostRecentItem()
                assertEquals(
                    "limit must reset to PAGE_SIZE even when the search text did not change",
                    PAGE_SIZE,
                    afterSameText.sessions.size,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Double load-more: limit increments twice before the pipeline settles
    // ---------------------------------------------------------------------------

    @Test
    fun `onLoadMore twice increments limit by two full pages`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // 55 rows: enough to fill PAGE_SIZE*2=40 but not PAGE_SIZE*3=60.
            val rows = (1..55).map { i -> sessionWithStats(makeSession("s$i", "u1")) }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertEquals(PAGE_SIZE, initial.sessions.size) // 20

                // Fire both load-mores before the pipeline settles.
                vm.onLoadMore() // limit → 40
                vm.onLoadMore() // limit → 60
                advanceUntilIdle()

                // rows.take(60) = 55; size < limit, so canLoadMore = false.
                val settled = expectMostRecentItem()
                assertEquals(55, settled.sessions.size)
                assertFalse(
                    "canLoadMore should be false when returned size (55) < limit (60)",
                    settled.canLoadMore,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Null-user path
    // ---------------------------------------------------------------------------

    @Test
    fun `null user yields empty sessions and isLoading false`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModelWith(
                userId = null,
                rowsByLimit = { emptyList() },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(emptyList<Any>(), settled.sessions)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `null user state has default RecordsTotals`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModelWith(
                userId = null,
                rowsByLimit = { emptyList() },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(
                    "null-user state must carry default RecordsTotals (no sessionCount, etc.)",
                    RecordsTotals(),
                    settled.totals,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Totals surfaced in state
    // ---------------------------------------------------------------------------

    @Test
    fun `state totals reflect repository totals`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val expectedTotals = RecordsTotals(sessionCount = 5, totalSamples = 30, totalEpg = 12)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                totals = expectedTotals,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(expectedTotals.sessionCount, settled.totals.sessionCount)
                assertEquals(expectedTotals.totalSamples, settled.totals.totalSamples)
                assertEquals(expectedTotals.totalEpg, settled.totals.totalEpg)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------

    private fun viewModelWith(
        userId: String?,
        rowsByLimit: (Int) -> List<SessionWithStats>,
        totals: RecordsTotals = RecordsTotals(),
    ): RecordsViewModel {
        val sessionRepo = LambdaSessionRepository(rowsByLimit, totals)
        val detectionRepo = FakeDetectionRepository(emptyMap())
        val authRepo = FakeAuthRepository(userId)
        val useCase = GetRecordsUseCase(authRepo, sessionRepo, detectionRepo)
        return RecordsViewModel(useCase)
    }

    private fun viewModelWithRecording(
        sessionRepo: RecordingSessionRepository,
        userId: String = "u1",
    ): RecordsViewModel {
        val detectionRepo = FakeDetectionRepository(emptyMap())
        val authRepo = FakeAuthRepository(userId)
        val useCase = GetRecordsUseCase(authRepo, sessionRepo, detectionRepo)
        return RecordsViewModel(useCase)
    }
}

// ---------------------------------------------------------------------------
// Recording fake session repository (captures query args for debounce assertions)
// ---------------------------------------------------------------------------

private class RecordingSessionRepository(
    private val rowsByLimit: (Int) -> List<SessionWithStats> = { emptyList() },
    private val totals: RecordsTotals = RecordsTotals(),
) : SessionRepository {
    /** All query strings that reached observeSessionRecordsPage, in call order. */
    val capturedQueries = mutableListOf<String>()

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
    ): Flow<List<SessionWithStats>> {
        capturedQueries += query
        return flowOf(rowsByLimit(limit))
    }

    override fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(totals)

    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())

    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

// ---------------------------------------------------------------------------
// Controllable fake session repository
// ---------------------------------------------------------------------------

private class LambdaSessionRepository(
    private val rowsByLimit: (Int) -> List<SessionWithStats>,
    private val totals: RecordsTotals = RecordsTotals(),
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
    ): Flow<List<SessionWithStats>> = flowOf(rowsByLimit(limit))
    override fun observeSessionRecordsTotals(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(totals)
    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

// ---------------------------------------------------------------------------
// Model helpers
// ---------------------------------------------------------------------------

private fun makeSession(id: String, userId: String): Session =
    Session(
        id = id,
        userId = userId,
        deviceId = "device-1",
        startedAt = 1_000L,
        endedAt = 2_000L,
        notes = null,
        label = null,
    )

private fun sessionWithStats(session: Session): SessionWithStats =
    SessionWithStats(
        session = session,
        totalSamples = 1,
        verifiedSamples = 1,
        unverifiedSamples = 0,
        totalEpg = 0,
    )
