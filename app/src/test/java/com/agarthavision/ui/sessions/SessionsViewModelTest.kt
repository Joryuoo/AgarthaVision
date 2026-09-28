package com.agarthavision.ui.sessions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.GenerateSessionLabelUseCase
import com.agarthavision.domain.usecase.sync.ObserveSyncInProgressUseCase
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.agarthavision.data.local.entity.SessionEntity

// One subject, one fixture. Splitting by concern would duplicate the ViewModel setup across
// files and make the duplicate-label cases harder to read against the rest of the suite.
@Suppress("LargeClass")
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
        private const val SEARCH_DEBOUNCE_MS = 300L
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
            // The VM forwards the null rather than deciding anything with it. What a
            // signed-out reader is allowed to see is settled one layer down — see
            // SessionRepositoryImplTest, where the answer is "nothing".
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
    // PB-09c: the list is scoped to the patient in the route
    // ---------------------------------------------------------------------------

    @Test
    fun `the route patient id is passed to the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                // Scoping lives in SQL, not in a filter over the loaded page: the list is
                // paginated, so a client-side filter would drop rows from the count and
                // shrink the page without ever looking wrong.
                assertTrue(
                    "every page query must be scoped to the route's patient",
                    recording.capturedArgs.isNotEmpty() &&
                        recording.capturedArgs.all { it.patientId == "patient-1" },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a route with no patient id resolves to an error instead of loading forever`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = buildViewModelWithIdentityFlow(
                repo = recording,
                identityFlow = MutableStateFlow(LocalIdentity("u1", "u1@example.com")),
                deps = IdentityFlowDeps(patientId = null),
            )

            vm.state.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()

                assertFalse("a missing patient must not hang on the loading skeleton", state.isLoading)
                assertNotNull(state.errorMessage)
                assertTrue(
                    "no query should be issued without a patient to scope it to",
                    recording.capturedArgs.isEmpty(),
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
            // Not summed from the loaded page: the list is paginated, so a local sum would
            // report only what had been scrolled into view.
            val expected = SessionsCounts(totalCount = 7, unverifiedCount = 3)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = expected,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(expected.totalCount, settled.totalCount)
                assertEquals(expected.unverifiedCount, settled.unverifiedCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // A sync in progress holds back a confident empty result (86d4c2q2e)
    // ---------------------------------------------------------------------------

    @Test
    fun `syncing with an empty unfiltered result stays loading until sync finishes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val syncing = MutableStateFlow(true)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = SessionsCounts(totalCount = 0),
                syncInProgressFlow = syncing,
            )

            vm.state.test {
                advanceUntilIdle()
                val whileSyncing = expectMostRecentItem()
                assertTrue(
                    "an empty, unfiltered result during a sync is unknown, not settled-empty",
                    whileSyncing.isLoading,
                )

                syncing.value = false
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(0, settled.totalCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `syncing with a non-empty result settles immediately`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val syncing = MutableStateFlow(true)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = SessionsCounts(totalCount = 7),
                syncInProgressFlow = syncing,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(
                    "existing data means there is no need to wait on the sync",
                    settled.isLoading,
                )
                assertEquals(7, settled.totalCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a filtered search does not wait on a sync in progress`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val syncing = MutableStateFlow(true)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = SessionsCounts(totalCount = 0),
                syncInProgressFlow = syncing,
            )

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem() // initial (loading) settle, discarded

                vm.onSearchQueryChanged("X")
                advanceTimeBy(SEARCH_DEBOUNCE_MS)
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(
                    "a filtered/searched empty result is settled regardless of sync state",
                    settled.isLoading,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a sync that never resolves leaves an unfiltered zero-session patient stuck loading`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Genuinely zero sessions AND a sync that never completes (e.g. offline with no
            // timeout, or stuck retrying). This documents that isLoading has no escape hatch
            // here: the screen has no way to distinguish "still syncing" from "sync is wedged"
            // without SyncScheduler itself resolving isSyncing to false at some point.
            val syncing = MutableStateFlow(true)
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                counts = SessionsCounts(totalCount = 0),
                syncInProgressFlow = syncing,
            )

            val collectJob = launch { vm.state.collect { } }
            advanceUntilIdle()
            assertTrue("stays loading while sync is in progress", vm.state.value.isLoading)

            // Advance well past any plausible UI timeout — nothing here ever settles it.
            advanceTimeBy(60_000)
            advanceUntilIdle()
            assertTrue(
                "with no sync completion signal, the screen stays loading indefinitely " +
                    "(no client-side timeout exists to unstick it)",
                vm.state.value.isLoading,
            )
            collectJob.cancel()
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
    // Duplicate label rejection (86d4bzjhw)
    // ---------------------------------------------------------------------------

    @Test
    fun `onCreateSession sets DUPLICATE_LABEL error when label is already taken for this patient`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val repo = DuplicateLabelSessionRepository(takenLabel = "GARCIAM-S01")
            val vm = buildViewModel(repo, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onCreateSession("GarciaM-S01")
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals(
                    "onCreateSession must surface DUPLICATE_LABEL when the label is taken",
                    "A smear with this label already exists for this patient.",
                    state.errorMessage,
                )
                assertFalse(
                    "isCreating must be false after duplicate rejection",
                    state.isCreating,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onCreateSession does not reject a label that is not taken`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // No taken labels — the duplicate pre-check passes. We stub startSession to throw a
            // known non-duplicate error so runCatching maps it to a generic message, not the
            // DUPLICATE_LABEL string, which is what this test asserts.
            val repo = DuplicateLabelSessionRepository(takenLabel = "OtherLabel-S01")
            val sessionManager = mock<SessionManager> {
                on { state } doReturn MutableStateFlow<SessionState>(SessionState.Idle)
                onBlocking { startSession(any(), any()) } doThrow RuntimeException("test-stub-create-failed")
            }
            val vm = buildViewModelWithSessionManager(repo, sessionManager, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onCreateSession("GarciaM-S01")
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(
                    "A non-duplicate label must not produce DUPLICATE_LABEL error; got: ${state.errorMessage}",
                    state.errorMessage == "A smear with this label already exists for this patient.",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onRenameSession sets DUPLICATE_LABEL error when new label is already taken for this patient`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val inputLabel = "GarciaM-S02"
            val repo = DuplicateLabelSessionRepository(
                takenLabel = "GARCIAM-S02",
                existingSession = makeSession("s1", "u1"),
            )
            val vm = buildViewModel(repo, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onRenameSession("s1", inputLabel)
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals(
                    "onRenameSession must surface DUPLICATE_LABEL when the new label is already taken",
                    "A smear with this label already exists for this patient.",
                    state.errorMessage,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onRenameSession allows keeping the same label (self-rename exclusion)`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Renaming s1 to its own current label must NOT be blocked. The excludingSessionId
            // parameter ensures the session's own label is not counted as a collision.
            // A successful rename emits no state change (onSuccess has no state update), so we
            // verify no DUPLICATE_LABEL error appears in any state emitted after the call.
            val ownLabel = "GarciaM-S01"
            val repo = DuplicateLabelSessionRepository(
                takenLabel = ownLabel,
                existingSession = makeSession("s1", "u1"),
                excludeSelfFromTaken = true,   // fake honours excludingSessionId
            )
            val vm = buildViewModel(repo, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertNull("errorMessage must be null before rename", initial.errorMessage)

                vm.onRenameSession("s1", ownLabel)
                advanceUntilIdle()

                // A successful rename emits no state change (onSuccess has no state update).
                // If DUPLICATE_LABEL had been set there would be a pending item here; there must not be.
                expectNoEvents()
                cancel()
            }
        }

    @Test
    fun `onCreateSession stores label as uppercase even when typed in lowercase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // A medtech types "smear 1" — the VM must uppercase it before writing and before
            // checking uniqueness, so the unique index (which is case-sensitive in SQLite) sees
            // the same form as auto-generated labels.
            val successEntity = SessionEntity(
                sessionId = "sess-new",
                userId = "u1",
                patientId = "patient-1",
                deviceId = "device-1",
                startedAt = 1_000L,
                label = "SMEAR 1",
            )
            val sessionManager = mock<SessionManager> {
                on { state } doReturn MutableStateFlow<SessionState>(SessionState.Idle)
                onBlocking { startSession(any(), any()) } doReturn successEntity
            }
            val repo = DuplicateLabelSessionRepository(takenLabel = "__nothing_taken__")
            val vm = buildViewModelWithSessionManager(repo, sessionManager, userId = "u1")

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onCreateSession("smear 1")
                advanceUntilIdle()

                val labelCaptor = argumentCaptor<String>()
                verify(sessionManager).startSession(label = labelCaptor.capture(), patientId = eq("patient-1"))
                assertEquals(
                    "onCreateSession must uppercase and trim the label before calling startSession",
                    "SMEAR 1",
                    labelCaptor.firstValue,
                )
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
                vm.onCreateSession("")
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

                vm.onCreateSession("")
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
    // 14zcqntj20y: the empty/initial query is exempt from the 300ms debounce
    // ---------------------------------------------------------------------------

    @Test
    fun `fresh viewModel queries repository without waiting out the debounce window`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                // Do not call advanceUntilIdle() first — that would mask a still-present
                // 300ms delay. runCurrent() only drains work scheduled for "now"; if the
                // empty query were still debounced, no repo call would exist yet here.
                testScheduler.runCurrent()

                assertTrue(
                    "the initial empty search query must not wait out SEARCH_DEBOUNCE_MS " +
                        "before the first repo query fires",
                    recording.capturedArgs.isNotEmpty(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `non-empty query typed immediately still waits out the full debounce window`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingSessionRepository()
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                testScheduler.runCurrent()
                val countAfterInitial = recording.capturedArgs.size

                vm.onSearchQueryChanged("x")
                testScheduler.runCurrent()
                assertEquals(
                    "a non-empty query must not fire before its debounce window elapses",
                    countAfterInitial,
                    recording.capturedArgs.size,
                )

                advanceTimeBy(SEARCH_DEBOUNCE_MS)
                testScheduler.runCurrent()
                assertTrue(
                    "a non-empty query must fire once its 300ms debounce window elapses",
                    recording.capturedArgs.size > countAfterInitial,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Patient Identity Preview Header
    // ---------------------------------------------------------------------------

    @Test
    fun `state emits patient and resolved barangay name when patient exists`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = defaultTestPatient
            val patientRepo = mock<PatientRepository> {
                on { observePatientById("patient-1") } doReturn flowOf(patient)
            }
            val psgcRepo = mock<PsgcRepository> {
                onBlocking { getBarangay("072217001") } doReturn PsgcBarangay(
                    code = "072217001",
                    name = "Poblacion",
                    cityMuniName = "Cebu City",
                    provinceName = "Cebu",
                    regionName = "Region VII",
                )
            }
            val vm = buildViewModelWithIdentityFlow(
                repo = LambdaSessionRepository({ emptyList() }),
                identityFlow = MutableStateFlow(LocalIdentity("u1", "u1@test.com")),
                deps = IdentityFlowDeps(patientRepo = patientRepo, psgcRepo = psgcRepo, patientId = "patient-1"),
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals("Dela Cruz, Juan D.", settled.patient?.displayName)
                assertEquals(Sex.MALE, settled.patient?.sex)
                assertEquals("Poblacion", settled.barangayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `state has null patient and null barangayName when patient is not found`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patientRepo = mock<PatientRepository> {
                on { observePatientById("unknown-patient") } doReturn flowOf(null)
            }
            val vm = buildViewModelWithIdentityFlow(
                repo = LambdaSessionRepository({ emptyList() }),
                identityFlow = MutableStateFlow(LocalIdentity("u1", "u1@test.com")),
                deps = IdentityFlowDeps(patientRepo = patientRepo, patientId = "unknown-patient"),
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertNull(settled.patient)
                assertNull(settled.barangayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `state updates patient and barangay when patient flow re-emits`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patientSource = MutableStateFlow<Patient?>(defaultTestPatient)
            val patientRepo = mock<PatientRepository> {
                on { observePatientById("patient-1") } doReturn patientSource
            }
            val psgcRepo = mock<PsgcRepository> {
                onBlocking { getBarangay("072217001") } doReturn PsgcBarangay(
                    code = "072217001",
                    name = "Poblacion",
                    cityMuniName = "Cebu City",
                    provinceName = "Cebu",
                    regionName = "Region VII",
                )
                onBlocking { getBarangay("072217002") } doReturn PsgcBarangay(
                    code = "072217002",
                    name = "San Roque",
                    cityMuniName = "Cebu City",
                    provinceName = "Cebu",
                    regionName = "Region VII",
                )
            }
            val vm = buildViewModelWithIdentityFlow(
                repo = LambdaSessionRepository({ emptyList() }),
                identityFlow = MutableStateFlow(LocalIdentity("u1", "u1@test.com")),
                deps = IdentityFlowDeps(patientRepo = patientRepo, psgcRepo = psgcRepo, patientId = "patient-1"),
            )

            vm.state.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem()
                assertEquals("Poblacion", initial.barangayName)

                patientSource.value = defaultTestPatient.copy(
                    lastname = "Santos",
                    psgcBarangayCode = "072217002",
                )
                advanceUntilIdle()
                val updated = expectMostRecentItem()
                assertEquals("Santos, Juan D.", updated.patient?.displayName)
                assertEquals("San Roque", updated.barangayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------

    private val defaultTestPatient = Patient(
        id = "patient-1",
        lastname = "Dela Cruz",
        firstname = "Juan",
        middleName = "Diaz",
        sex = Sex.MALE,
        birthdate = LocalDate.of(2000, 1, 1),
        psgcBarangayCode = "072217001",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun stubPatientRepository(patient: Patient? = defaultTestPatient): PatientRepository =
        mock<PatientRepository> {
            on { observePatientById(any()) } doReturn flowOf(patient)
            onBlocking { getPatientById(any()) } doReturn patient
        }

    private fun stubPsgcRepository(barangayName: String? = "Poblacion"): PsgcRepository =
        mock<PsgcRepository> {
            onBlocking { getBarangay(any()) } doReturn barangayName?.let {
                PsgcBarangay(
                    code = "072217001",
                    name = it,
                    cityMuniName = "Cebu City",
                    provinceName = "Cebu",
                    regionName = "Region VII",
                )
            }
        }

    private fun viewModelWith(
        userId: String?,
        rowsByLimit: (Int) -> List<SessionWithStats>,
        counts: SessionsCounts = SessionsCounts(),
        syncInProgressFlow: Flow<Boolean> = flowOf(false),
    ): SessionsViewModel {
        val repo = LambdaSessionRepository(rowsByLimit, counts)
        return buildViewModel(repo, userId, syncInProgressFlow)
    }

    private fun viewModelWithRecording(
        repo: RecordingSessionRepository,
        userId: String? = "u1",
    ): SessionsViewModel = buildViewModel(repo, userId)

    private fun buildViewModel(
        repo: SessionRepository,
        userId: String?,
        syncInProgressFlow: Flow<Boolean> = flowOf(false),
    ): SessionsViewModel {
        val identityFlow = MutableStateFlow(
            userId?.let { LocalIdentity(userId = it, email = "user@example.com") }
        )
        return buildViewModelWithIdentityFlow(
            repo,
            identityFlow,
            deps = IdentityFlowDeps(syncInProgressFlow = syncInProgressFlow),
        )
    }

    private fun stubSyncInProgressUseCase(flow: Flow<Boolean> = flowOf(false)): ObserveSyncInProgressUseCase =
        mock<ObserveSyncInProgressUseCase>().also {
            whenever(it.invoke()).thenReturn(flow)
        }

    /** Variant that accepts a pre-configured [sessionManager] (e.g. with startSession stubbed). */
    private fun buildViewModelWithSessionManager(
        repo: SessionRepository,
        sessionManager: SessionManager,
        userId: String?,
        patientRepo: PatientRepository = stubPatientRepository(),
        psgcRepo: PsgcRepository = stubPsgcRepository(),
    ): SessionsViewModel {
        val observeLocalIdentityUseCase = mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                MutableStateFlow(userId?.let { id -> LocalIdentity(userId = id, email = "user@example.com") })
            )
        }
        return SessionsViewModel(
            sessionRepository = repo,
            sessionManager = sessionManager,
            observeLocalIdentityUseCase = observeLocalIdentityUseCase,
            generateSessionLabelUseCase = stubLabelUseCase(),
            patientRepository = patientRepo,
            psgcRepository = psgcRepo,
            observeSyncInProgressUseCase = stubSyncInProgressUseCase(),
            savedStateHandle = SavedStateHandle(mapOf("patientId" to "patient-1")),
        )
    }

    /**
     * The generator is exercised by its own pure tests; here it only has to not be null. The
     * VM calls it from `init`, so an unstubbed mock would return null from a non-null
     * `Result` and take down every test in this file.
     */
    private fun stubLabelUseCase() = mock<GenerateSessionLabelUseCase> {
        onBlocking { invoke(any(), any()) } doReturn Result.success("C.G.-0730600000-001")
    }

    /**
     * Optional collaborators for [buildViewModelWithIdentityFlow] — bundled since callers only
     * ever override a few at a time.
     */
    private data class IdentityFlowDeps(
        val patientRepo: PatientRepository? = null,
        val psgcRepo: PsgcRepository? = null,
        val patientId: String? = "patient-1",
        val syncInProgressFlow: Flow<Boolean> = flowOf(false),
    )

    private fun buildViewModelWithIdentityFlow(
        repo: SessionRepository,
        identityFlow: MutableStateFlow<LocalIdentity?>,
        deps: IdentityFlowDeps = IdentityFlowDeps(),
    ): SessionsViewModel {
        val observeLocalIdentityUseCase = mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(identityFlow)
        }
        // The VM reads the active session id off this flow to exempt the open smear from
        // the date filter, so an unstubbed mock makes every test here NPE on collect.
        val sessionManager = mock<SessionManager> {
            on { state } doReturn MutableStateFlow<SessionState>(SessionState.Idle)
        }
        // The screen is reached at `patients/{patientId}`, so the VM reads the patient it
        // creates sessions for straight off the route.
        return SessionsViewModel(
            sessionRepository = repo,
            sessionManager = sessionManager,
            observeLocalIdentityUseCase = observeLocalIdentityUseCase,
            generateSessionLabelUseCase = stubLabelUseCase(),
            patientRepository = deps.patientRepo ?: stubPatientRepository(),
            psgcRepository = deps.psgcRepo ?: stubPsgcRepository(),
            observeSyncInProgressUseCase = stubSyncInProgressUseCase(deps.syncInProgressFlow),
            savedStateHandle = SavedStateHandle(
                if (deps.patientId != null) mapOf("patientId" to deps.patientId) else emptyMap()
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Recording fake — captures page args for assertion
// ---------------------------------------------------------------------------

private data class PageArgs(
    val userId: String?,
    val patientId: String,
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

    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?,
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
    ): Flow<List<SessionWithStats>> {
        capturedArgs += PageArgs(userId, patientId, sinceMillis, startMillis, endMillis, query, limit)
        return flowOf(rows.take(limit))
    }

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

// ---------------------------------------------------------------------------
// Controllable fake — page source backed by MutableStateFlow so the repo can
// re-emit new pages to trigger the combine re-evaluation in-test.
// ---------------------------------------------------------------------------

private class ControllableSessionRepository(
    initialRows: List<SessionWithStats> = emptyList(),
) : SessionRepository {
    val pageSource = MutableStateFlow(initialRows)

    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?,
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
    ): Flow<List<SessionWithStats>> = pageSource

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

// ---------------------------------------------------------------------------
// Lambda fake — drives page size via rowsByLimit lambda
// ---------------------------------------------------------------------------

private class LambdaSessionRepository(
    private val rowsByLimit: (Int) -> List<SessionWithStats>,
    private val counts: SessionsCounts = SessionsCounts(),
) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): Session? = null
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionRecordsPage(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?, limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?, startMillis: Long?, endMillis: Long?, query: String, species: String?,
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
    ): Flow<List<SessionWithStats>> = flowOf(rowsByLimit(limit))

    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(counts)
}

// ---------------------------------------------------------------------------
// Duplicate-label fake — returns true from isSessionLabelTaken for a specific label.
// ---------------------------------------------------------------------------

/**
 * Fake that simulates a taken label. Pass [excludeSelfFromTaken] = true to simulate the
 * real DAO behaviour where the session being renamed is excluded from the collision count
 * (the `excludingSessionId != session_id` clause in the query).
 */
private class DuplicateLabelSessionRepository(
    private val takenLabel: String,
    private val existingSession: SessionWithStats? = null,
    private val excludeSelfFromTaken: Boolean = false,
) : SessionRepository {
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean {
        // When excludeSelfFromTaken is true and a session id is given, pretend the exclusion
        // means the label is NOT taken for that specific rename — matching real DAO semantics.
        if (excludeSelfFromTaken && excludingSessionId != null) return false
        return label == takenLabel
    }

    override suspend fun getSessionById(sessionId: String): Session? =
        existingSession?.session?.takeIf { it.id == sessionId }

    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(emptyList())
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
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

// ---------------------------------------------------------------------------
// Model helpers
// ---------------------------------------------------------------------------

private fun makeSession(id: String, userId: String): SessionWithStats =
    SessionWithStats(
        session = Session(
            id = id,
            userId = userId,
            patientId = "patient-1",
            deviceId = "device-1",
            startedAt = Instant.EPOCH.toEpochMilli(),
            label = "Smear $id",
        ),
        totalSamples = 0,
        verifiedSamples = 0,
        unverifiedSamples = 0,
        totalEggs = 0,
    )
