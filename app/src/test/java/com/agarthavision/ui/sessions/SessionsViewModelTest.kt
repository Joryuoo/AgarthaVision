package com.agarthavision.ui.sessions

import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SetSessionClaimExemptUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ---------------------------------------------------------------------------
    // Constants mirroring the VM — kept here so test intent is explicit.
    // ---------------------------------------------------------------------------

    companion object {
        private const val INITIAL_PAGE = 5
        private const val PAGE_STEP = 10
    }

    // ---------------------------------------------------------------------------
    // First page is 5 rows
    // ---------------------------------------------------------------------------

    @Test
    fun `first page contains up to INITIAL_PAGE sessions`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..10).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(INITIAL_PAGE, settled.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // onLoadMore grows the limit
    // ---------------------------------------------------------------------------

    @Test
    fun `onLoadMore increments limit by PAGE_STEP`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..30).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertEquals(INITIAL_PAGE, initial.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                val afterOne = expectMostRecentItem()
                assertEquals(INITIAL_PAGE + PAGE_STEP, afterOne.sessions.size)

                vm.onLoadMore()
                advanceUntilIdle()
                val afterTwo = expectMostRecentItem()
                assertEquals(INITIAL_PAGE + PAGE_STEP * 2, afterTwo.sessions.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // canLoadMore: true when size == limit, false when less
    // ---------------------------------------------------------------------------

    @Test
    fun `canLoadMore is true when returned size equals limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..INITIAL_PAGE).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertTrue("canLoadMore should be true when size == limit", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canLoadMore is false when returned size is less than limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..3).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse("canLoadMore should be false when size < limit", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // onDateRangeSelected resets limit and passes start-of-day / end-of-day millis
    // ---------------------------------------------------------------------------

    @Test
    fun `onDateRangeSelected resets limit to INITIAL_PAGE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..30).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
            )

            vm.state.test {
                advanceUntilIdle()
                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem() // expanded

                vm.onDateRangeSelected(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(
                    "onDateRangeSelected must reset limit to INITIAL_PAGE",
                    INITIAL_PAGE,
                    afterReset.sessions.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDateRangeSelected passes correct start-of-day and end-of-day millis to repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val start = LocalDate.of(2026, 8, 12)
            val end = LocalDate.of(2026, 8, 19)
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                advanceUntilIdle()
                val baseCount = recording.capturedArgs.size

                vm.onDateRangeSelected(start, end)
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(baseCount)
                assertTrue("onDateRangeSelected should trigger at least one repo call", newArgs.isNotEmpty())

                val zone = ZoneId.systemDefault()
                val expectedStart = start.atStartOfDay(zone).toInstant().toEpochMilli()
                val expectedEnd = end.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1).toEpochMilli()
                val last = newArgs.last()
                assertEquals("startMillis should be start-of-day", expectedStart, last.startMillis)
                assertEquals("endMillis should be end-of-day - 1ms", expectedEnd, last.endMillis)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDateRangeSelected clamps a future range to today before querying`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val today = LocalDate.now()
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                advanceUntilIdle()
                val baseCount = recording.capturedArgs.size

                vm.onDateRangeSelected(today.plusDays(1), today.plusDays(30))
                advanceUntilIdle()

                val settled = expectMostRecentItem()
                assertEquals("future start must clamp to today", today, settled.startDate)
                assertEquals("future end must clamp to today", today, settled.endDate)

                val zone = ZoneId.systemDefault()
                val last = recording.capturedArgs.drop(baseCount).last()
                assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), last.startMillis)
                assertEquals(
                    today.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1).toEpochMilli(),
                    last.endMillis,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // onSearchQueryChanged: state.searchQuery is synchronous, limit resets, debounce
    // ---------------------------------------------------------------------------

    @Test
    fun `onSearchQueryChanged reflects raw query immediately in state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModelWith(userId = "u1", rowsByLimit = { emptyList() })

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSearchQueryChanged("abc")
                // searchQuery raw flow updates internalState synchronously; no debounce needed.
                advanceTimeBy(50)

                val mid = expectMostRecentItem()
                assertEquals(
                    "state.searchQuery should reflect raw input before debounce elapses",
                    "abc",
                    mid.searchQuery,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onSearchQueryChanged resets limit to INITIAL_PAGE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..30).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(userId = "u1", rowsByLimit = { limit -> rows.take(limit) })

            vm.state.test {
                advanceUntilIdle()
                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSearchQueryChanged("foo")
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(INITIAL_PAGE, afterReset.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `needle is escaped before reaching repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSearchQueryChanged("100%_done\\")
                advanceUntilIdle()

                val queries = recording.capturedArgs.map { it.query }
                assertEquals(
                    "LIKE-special characters must be escaped before reaching repo",
                    "100\\%\\_done\\\\",
                    queries.last(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Null identity → userId null in repo call
    // ---------------------------------------------------------------------------

    @Test
    fun `null identity passes null userId to repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording, userId = null)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                assertTrue(
                    "observeVisibleSessionsPage must be called with userId=null",
                    recording.capturedArgs.any { it.userId == null },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Header counts come from SessionsCounts
    // ---------------------------------------------------------------------------

    @Test
    fun `header counts are sourced from repository SessionsCounts`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val expected = SessionsCounts(totalCount = 7, activeCount = 3)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = expected,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(expected.totalCount, settled.totalCount)
                assertEquals(expected.activeCount, settled.activeCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // stateIn WhileSubscribed retains last value across resubscribe
    // ---------------------------------------------------------------------------

    @Test
    fun `stateIn WhileSubscribed retains last value after upstream cancellation`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..INITIAL_PAGE).map { makeSession("s$it", "u1") }
            val vm = viewModelWith(userId = "u1", rowsByLimit = { limit -> rows.take(limit) })

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                cancelAndIgnoreRemainingEvents()
            }

            // Advance past WhileSubscribed 5-second stop timeout.
            advanceTimeBy(6_000)

            vm.state.test {
                val resubscribed = awaitItem()
                assertFalse(
                    "stateIn replay should return last non-loading state",
                    resubscribed.isLoading,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Identity change mid-session
    // ---------------------------------------------------------------------------

    /**
     * Emitting null from the identity flow (sign-out) must cause a fresh repo call
     * with userId=null. The VM does NOT reset the limit on identity change — the
     * screen relies on the user to explicitly reset via search/date — so we document
     * that the new call carries whatever limit was active at the time.
     */
    @Test
    fun `identity change from non-null to null re-queries repo with null userId`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val identityFlow = MutableStateFlow<LocalIdentity?>(
                LocalIdentity(userId = "u1", email = "user@example.com")
            )
            val vm = buildViewModelWithIdentityFlow(recording, identityFlow)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                val countBefore = recording.capturedArgs.size
                identityFlow.value = null
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue("signing out must trigger at least one new repo call", newArgs.isNotEmpty())
                assertTrue(
                    "repo must be called with userId=null after sign-out",
                    newArgs.any { it.userId == null },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `identity change from null to non-null re-queries repo with the new userId`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val identityFlow = MutableStateFlow<LocalIdentity?>(null)
            val vm = buildViewModelWithIdentityFlow(recording, identityFlow)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                val countBefore = recording.capturedArgs.size
                identityFlow.value = LocalIdentity(userId = "u2", email = "user2@example.com")
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue("signing in must trigger at least one new repo call", newArgs.isNotEmpty())
                assertTrue(
                    "repo must be called with the new userId after sign-in",
                    newArgs.any { it.userId == "u2" },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Documents VM behaviour: limit is NOT reset when the identity changes, because
     * the VM only resets limit on onSearchQueryChanged / onDateRangeSelected.
     * The screen guards canLoadMore but does not hold back an in-flight limit.
     */
    @Test
    fun `limit is not reset when identity changes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val identityFlow = MutableStateFlow<LocalIdentity?>(
                LocalIdentity(userId = "u1", email = "user@example.com")
            )
            val vm = buildViewModelWithIdentityFlow(recording, identityFlow)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                // Expand the limit to INITIAL_PAGE + PAGE_STEP = 15.
                vm.onLoadMore()
                advanceUntilIdle()
                // Do NOT call expectMostRecentItem() here: the repo returns emptyList() for
                // both limit=5 and limit=15, so the combined state doesn't change and turbine
                // has no pending item. We only care about the capturedArgs below.

                val countBefore = recording.capturedArgs.size
                // Now change identity — this must NOT reset limit back to INITIAL_PAGE.
                identityFlow.value = null
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue("identity change must trigger at least one new repo call", newArgs.isNotEmpty())
                // The limit in any new call must still be the expanded value (15), not INITIAL_PAGE (5).
                val expandedLimit = INITIAL_PAGE + PAGE_STEP
                assertTrue(
                    "limit must NOT be reset by identity change; expected all new calls to carry limit=$expandedLimit",
                    newArgs.all { it.limit == expandedLimit },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // sinceMillis: ~30 days before now, passed even with date range
    // ---------------------------------------------------------------------------

    @Test
    fun `sinceMillis is approximately 30 days before now`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val nowBefore = System.currentTimeMillis()
            val recording = RecordingSessionRepository()
            val vm = buildViewModel(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                assertTrue("repo must have been called at least once", recording.capturedArgs.isNotEmpty())
                val sinceMillis = recording.capturedArgs.first().sinceMillis

                val thirtyOneDaysAgo = nowBefore - 31L * 24 * 60 * 60 * 1000
                val twentyNineDaysAgo = nowBefore - 29L * 24 * 60 * 60 * 1000
                assertTrue(
                    "sinceMillis ($sinceMillis) must be between 31 days ago and 29 days ago",
                    sinceMillis in thirtyOneDaysAgo..twentyNineDaysAgo,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sinceMillis is still passed to repo when date range is set`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val nowBefore = System.currentTimeMillis()
            val recording = RecordingSessionRepository()
            val vm = buildViewModel(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                val countBefore = recording.capturedArgs.size
                vm.onDateRangeSelected(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue("date range selection must trigger at least one repo call", newArgs.isNotEmpty())

                val thirtyOneDaysAgo = nowBefore - 31L * 24 * 60 * 60 * 1000
                val twentyNineDaysAgo = nowBefore - 29L * 24 * 60 * 60 * 1000
                val sinceMillis = newArgs.last().sinceMillis
                assertTrue(
                    "sinceMillis must still be ~30 days ago even when a date range is active; got $sinceMillis",
                    sinceMillis in thirtyOneDaysAgo..twentyNineDaysAgo,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Open-ended date range: startDate set, endDate null → endMillis null
    // ---------------------------------------------------------------------------

    /**
     * Documents VM behaviour: when start is set but end is null the VM passes
     * endMillis=null to the repository. Note the DAO does NOT treat this as an
     * open-ended range - its range branch requires `started_at <= :endMillis`, so a
     * null end collapses it and only active sessions match. DateRangeFilterBar
     * guarantees end is backfilled to start, so this combination never reaches SQL.
     */
    @Test
    fun `startDate set with null endDate passes null endMillis to repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = buildViewModel(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                val countBefore = recording.capturedArgs.size
                val start = LocalDate.of(2026, 6, 1)
                vm.onDateRangeSelected(start, null)
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue("open-ended range must trigger at least one repo call", newArgs.isNotEmpty())
                val last = newArgs.last()

                val zone = ZoneId.systemDefault()
                val expectedStart = start.atStartOfDay(zone).toInstant().toEpochMilli()
                assertEquals("startMillis must be start-of-day for the given date", expectedStart, last.startMillis)
                assertNull("endMillis must be null when no endDate is provided", last.endMillis)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Error state interplay
    // ---------------------------------------------------------------------------

    @Test
    fun `errorMessage set by blank onCreateSession survives subsequent page re-emission`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val controllable = ControllableSessionRepository(
                initialRows = (1..3).map { makeSession("s$it", "u1") },
            )
            val vm = buildViewModel(controllable, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertNull("errorMessage must be null before any error", initial.errorMessage)
                assertEquals(3, initial.sessions.size)

                // Trigger error via blank label.
                vm.onCreateSession("", notes = null)
                advanceUntilIdle()
                val withError = expectMostRecentItem()
                assertEquals("Label is required.", withError.errorMessage)

                // Cause the repo's page source to re-emit (simulates a Room update arriving with
                // a 4th session). The value must be structurally different from the current one
                // so that MutableStateFlow actually emits, producing a new combine evaluation.
                controllable.pageSource.value = (1..4).map { makeSession("s$it", "u1") }
                advanceUntilIdle()
                val afterReemit = expectMostRecentItem()
                assertEquals(
                    "errorMessage must survive a page re-emission from the repository",
                    "Label is required.",
                    afterReemit.errorMessage,
                )
                assertEquals(
                    "session list must reflect the updated page from re-emission",
                    4,
                    afterReemit.sessions.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDismissError clears errorMessage without resetting session list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val controllable = ControllableSessionRepository(
                initialRows = (1..3).map { makeSession("s$it", "u1") },
            )
            val vm = buildViewModel(controllable, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onCreateSession("", notes = null)
                advanceUntilIdle()
                val withError = expectMostRecentItem()
                assertEquals("Label is required.", withError.errorMessage)
                assertEquals(3, withError.sessions.size)

                vm.onDismissError()
                advanceUntilIdle()
                val afterDismiss = expectMostRecentItem()
                assertNull("onDismissError must clear errorMessage", afterDismiss.errorMessage)
                assertEquals(
                    "onDismissError must not reset the session list",
                    3,
                    afterDismiss.sessions.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // onLoadMore when canLoadMore is false still bumps limit
    // ---------------------------------------------------------------------------

    /**
     * Documents VM behaviour: onLoadMore unconditionally increments the limit
     * regardless of canLoadMore. The screen (UI) is responsible for hiding the
     * "Load more" button when canLoadMore=false; the VM does not guard against it.
     */
    @Test
    fun `onLoadMore when canLoadMore is false still increments limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // 3 rows < INITIAL_PAGE=5, so canLoadMore will be false.
            val rows = (1..3).map { makeSession("s$it", "u1") }
            val recording = RecordingSessionRepository(rows = rows)
            val vm = buildViewModel(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertFalse("canLoadMore must be false before load-more", initial.canLoadMore)
                assertEquals(3, initial.sessions.size)

                val countBefore = recording.capturedArgs.size
                // Calling onLoadMore even though canLoadMore is false.
                vm.onLoadMore()
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertTrue(
                    "onLoadMore must trigger a new repo call even when canLoadMore is false",
                    newArgs.isNotEmpty(),
                )
                val expectedLimit = INITIAL_PAGE + PAGE_STEP
                assertTrue(
                    "new repo call must carry the incremented limit ($expectedLimit), not INITIAL_PAGE",
                    newArgs.any { it.limit == expectedLimit },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Debounce: fast typing coalesces into a single repo call with the final query
    // ---------------------------------------------------------------------------

    @Test
    fun `rapid typing coalesces into a single repo call carrying the final query`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = buildViewModel(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertEquals("", initial.searchQuery)

                val countBefore = recording.capturedArgs.size

                // Three keystrokes inside the 300 ms debounce window.
                vm.onSearchQueryChanged("a")
                vm.onSearchQueryChanged("ab")
                vm.onSearchQueryChanged("abc")

                // Advance less than debounce — no new repo call yet but state reflects "abc".
                advanceTimeBy(250)
                val mid = expectMostRecentItem()
                assertEquals(
                    "state.searchQuery must reflect last keystroke before debounce fires",
                    "abc",
                    mid.searchQuery,
                )
                assertEquals(
                    "no new repo call must be made before debounce fires",
                    countBefore,
                    recording.capturedArgs.size,
                )

                // Now fire the debounce.
                advanceUntilIdle()

                val newArgs = recording.capturedArgs.drop(countBefore)
                assertEquals(
                    "debounce must coalesce rapid changes into exactly one repo call",
                    1,
                    newArgs.size,
                )
                assertEquals("the single repo call must carry the final query", "abc", newArgs.single().query)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------

    private fun viewModelWith(
        userId: String?,
        rowsByLimit: (Int) -> List<SessionWithStats>,
        counts: SessionsCounts = SessionsCounts(),
    ): SessionsViewModel {
        val repo = LambdaSessionRepository(rowsByLimit, counts)
        return buildViewModel(repo, userId)
    }

    private fun viewModelWithRecording(
        repo: RecordingSessionRepository,
        userId: String? = "u1",
    ): SessionsViewModel = buildViewModel(repo, userId)

    private fun buildViewModel(repo: SessionRepository, userId: String?): SessionsViewModel {
        val identityFlow = MutableStateFlow(
            userId?.let { LocalIdentity(userId = it, email = "user@example.com") }
        )
        return buildViewModelWithIdentityFlow(repo, identityFlow)
    }

    private fun buildViewModelWithIdentityFlow(
        repo: SessionRepository,
        identityFlow: MutableStateFlow<LocalIdentity?>,
    ): SessionsViewModel {
        val observeLocalIdentityUseCase = mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(identityFlow)
        }
        val sessionManager = mock<SessionManager>()
        val setExempt = mock<SetSessionClaimExemptUseCase>()
        val claim = mock<ClaimLocalDataUseCase>()
        return SessionsViewModel(
            sessionRepository = repo,
            sessionManager = sessionManager,
            observeLocalIdentityUseCase = observeLocalIdentityUseCase,
            setSessionClaimExemptUseCase = setExempt,
            claimLocalDataUseCase = claim,
        )
    }
}

// ---------------------------------------------------------------------------
// Recording fake — captures page args for assertion
// ---------------------------------------------------------------------------

private data class PageArgs(
    val userId: String?,
    val sinceMillis: Long,
    val startMillis: Long?,
    val endMillis: Long?,
    val query: String,
    val limit: Int,
)

private class RecordingSessionRepository(
    /** Rows returned from every observeVisibleSessionsPage call (ignores limit). */
    private val rows: List<SessionWithStats> = emptyList(),
) : SessionRepository {
    val capturedArgs = mutableListOf<PageArgs>()

    override fun observeAllSessions(userId: String): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun setClaimExempt(sessionId: String, exempt: Boolean) = Unit
    override suspend fun claimSession(sessionId: String, userId: String) = Unit
    override fun observeSessionRecordsPage(
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())

    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> {
        capturedArgs += PageArgs(userId, sinceMillis, startMillis, endMillis, query, limit)
        return flowOf(rows.take(limit))
    }

    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

// ---------------------------------------------------------------------------
// Controllable fake — page source backed by MutableStateFlow so the repo can
// re-emit new pages to trigger the combine re-evaluation in-test.
// ---------------------------------------------------------------------------

private class ControllableSessionRepository(
    initialRows: List<SessionWithStats> = emptyList(),
) : SessionRepository {
    val pageSource = MutableStateFlow(initialRows)

    override fun observeAllSessions(userId: String): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun setClaimExempt(sessionId: String, exempt: Boolean) = Unit
    override suspend fun claimSession(sessionId: String, userId: String) = Unit
    override fun observeSessionRecordsPage(
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())

    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = pageSource

    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

// ---------------------------------------------------------------------------
// Lambda fake — drives page size via rowsByLimit lambda
// ---------------------------------------------------------------------------

private class LambdaSessionRepository(
    private val rowsByLimit: (Int) -> List<SessionWithStats>,
    private val counts: SessionsCounts = SessionsCounts(),
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
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String, startMillis: Long?, endMillis: Long?, query: String, species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())

    override fun observeVisibleSessionsPage(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(rowsByLimit(limit))

    override fun observeVisibleSessionsCounts(
        userId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(counts)
}

// ---------------------------------------------------------------------------
// Model helpers
// ---------------------------------------------------------------------------

private fun makeSession(id: String, userId: String): SessionWithStats =
    SessionWithStats(
        session = Session(
            id = id,
            userId = userId,
            deviceId = "device-1",
            startedAt = Instant.EPOCH.toEpochMilli(),
            endedAt = null,
            notes = null,
            label = "Smear $id",
        ),
        totalSamples = 0,
        verifiedSamples = 0,
        unverifiedSamples = 0,
        totalEpg = 0,
    )
