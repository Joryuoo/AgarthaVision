package com.agarthavision.ui.records

import app.cash.turbine.test
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.FakeAuthRepository
import com.agarthavision.domain.usecase.records.FakeDetectionRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
                val initial = awaitItem()       // retained initial value
                val settled = if (initial.isLoading) awaitItem() else initial
                assertFalse(settled.isLoading)
                assertEquals(1, settled.sessions.size)
                assertEquals("s1", settled.sessions.single().session.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // CORE PART-B REGRESSION GUARD: resubscribe retains state
    // ---------------------------------------------------------------------------

    @Test
    fun `fresh collector receives retained non-empty state without resetting to loading`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val session = makeSession("s1", "u1")
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> listOf(sessionWithStats(session)).take(limit) },
            )

            // First collection — let the VM settle
            vm.state.test {
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertFalse(settled.isLoading)
                assertEquals(1, settled.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }

            // Second collection (fresh subscriber) — must immediately get the retained value
            vm.state.test {
                val resubscribed = awaitItem()
                // Must NOT be the default RecordsState (isLoading=true, sessions=empty)
                assertFalse(
                    "Resubscribed collector got isLoading=true — state was reset instead of retained",
                    resubscribed.isLoading,
                )
                assertEquals(
                    "Resubscribed collector got empty sessions — state was reset instead of retained",
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
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
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
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
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
                // Drain initial loading
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertEquals(PAGE_SIZE, settled.sessions.size)

                // Trigger load-more
                vm.onLoadMore()
                advanceUntilIdle()

                val afterLoadMore = awaitItem()
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
                // Drain initial
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertEquals(PAGE_SIZE, settled.sessions.size)

                // Expand limit
                vm.onLoadMore()
                advanceUntilIdle()
                awaitItem() // PAGE_SIZE + 10 items

                // Filter resets limit → back to PAGE_SIZE results
                vm.onSpeciesSelected(EggSpecies.ASCARIS)
                advanceUntilIdle()
                val afterReset = awaitItem()
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
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertEquals(PAGE_SIZE, settled.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                awaitItem()

                vm.onDateRangeSelected(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31))
                advanceUntilIdle()
                val afterReset = awaitItem()
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
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertEquals(PAGE_SIZE, settled.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                awaitItem()

                vm.onSearchChanged("smear")
                advanceUntilIdle()
                val afterReset = awaitItem()
                assertEquals(PAGE_SIZE, afterReset.sessions.size)
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
                val initial = awaitItem()
                val settled = if (initial.isLoading) awaitItem() else initial
                assertFalse(settled.isLoading)
                assertEquals(emptyList<Any>(), settled.sessions)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------

    private fun viewModelWith(
        userId: String?,
        rowsByLimit: (Int) -> List<SessionWithStats>,
    ): RecordsViewModel {
        val sessionRepo = LambdaSessionRepository(rowsByLimit)
        val detectionRepo = FakeDetectionRepository(emptyMap())
        val authRepo = FakeAuthRepository(userId)
        val useCase = GetRecordsUseCase(authRepo, sessionRepo, detectionRepo)
        return RecordsViewModel(useCase)
    }
}

// ---------------------------------------------------------------------------
// Controllable fake session repository
// ---------------------------------------------------------------------------

private class LambdaSessionRepository(
    private val rowsByLimit: (Int) -> List<SessionWithStats>,
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
