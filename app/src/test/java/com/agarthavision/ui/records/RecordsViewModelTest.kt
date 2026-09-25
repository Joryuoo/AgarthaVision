package com.agarthavision.ui.records

import app.cash.turbine.test
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.usecase.records.FakeAuthRepository
import com.agarthavision.domain.usecase.records.ObserveReportsUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
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
    fun `first emission sets isLoading false with reports from use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val report = makeReport("r1", "s1", "u1")
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> listOf(report).take(limit) },
                totalCount = 1,
            )

            vm.state.test {
                // Advance past the 300 ms debounce so the settled state is emitted.
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(1, settled.reports.size)
                assertEquals("r1", settled.reports.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // CORE REGRESSION GUARD: stateIn WhileSubscribed retains last value
    // after the upstream is cancelled (past the 5-second stop timeout).
    // ---------------------------------------------------------------------------

    @Test
    fun `stateIn WhileSubscribed retains last value after upstream cancellation`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val report = makeReport("r1", "s1", "u1")
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> listOf(report).take(limit) },
                totalCount = 1,
            )

            // First collection — let the VM settle to a non-loading state.
            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(1, settled.reports.size)
                cancelAndIgnoreRemainingEvents()
            }

            // Advance past the 5-second WhileSubscribed stop timeout so the upstream
            // Room query is actually cancelled, but stateIn's replay cache is preserved.
            advanceTimeBy(6_000)

            // Second collection — stateIn must immediately replay the last non-loading state.
            vm.state.test {
                val resubscribed = awaitItem()
                assertFalse(
                    "stateIn replay should return last non-loading state, not the initial default",
                    resubscribed.isLoading,
                )
                assertEquals(
                    "stateIn replay should return the last report list, not an empty one",
                    1,
                    resubscribed.reports.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Pagination: onLoadMore grows reports; canLoadMore reflects size vs totalCount
    // ---------------------------------------------------------------------------

    @Test
    fun `canLoadMore is true when returned size is less than totalCount`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..PAGE_SIZE).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = 25,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(PAGE_SIZE, settled.reports.size)
                assertTrue("canLoadMore should be true when size < totalCount", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canLoadMore is false when returned size equals totalCount`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..5).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = 5,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(5, settled.reports.size)
                assertFalse("canLoadMore should be false when size == totalCount", settled.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onLoadMore increases limit and grows report list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 5)).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = PAGE_SIZE + 5,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.reports.size)

                vm.onLoadMore()
                advanceUntilIdle()

                val afterLoadMore = expectMostRecentItem()
                assertEquals(PAGE_SIZE + 5, afterLoadMore.reports.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Filter setters reset limit
    // ---------------------------------------------------------------------------

    @Test
    fun `onSpeciesSelected resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = PAGE_SIZE + 10,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.reports.size)

                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSpeciesSelected(EggSpecies.ASCARIS)
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.reports.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDateRangeSelected resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = PAGE_SIZE + 10,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.reports.size)

                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onDateRangeSelected(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31))
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.reports.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onDateRangeSelected clamps a future range to today`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val today = LocalDate.now()
            val vm = viewModelWith(userId = "u1", rowsByLimit = { emptyList() })

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onDateRangeSelected(today.plusDays(1), today.plusDays(30))
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals("future start must clamp to today", today, settled.startDate)
                assertEquals("future end must clamp to today", today, settled.endDate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onSearchChanged resets limit to PAGE_SIZE`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = PAGE_SIZE + 10,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertEquals(PAGE_SIZE, settled.reports.size)

                vm.onLoadMore()
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onSearchChanged("ascaris")
                advanceUntilIdle()
                val afterReset = expectMostRecentItem()
                assertEquals(PAGE_SIZE, afterReset.reports.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Debounce: fast typing coalesces into a single repo call
    // ---------------------------------------------------------------------------

    @Test
    fun `typing fast - searchQuery in state reflects raw input before debounce and repo called only once`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val recording = RecordingReportRepository(rowsByLimit = { emptyList() })
            val vm = viewModelWithRecording(recording)

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                val baseCallCount = recording.capturedQueries.size

                vm.onSearchChanged("a")
                vm.onSearchChanged("ab")
                vm.onSearchChanged("abc")

                advanceTimeBy(250)

                val midState = expectMostRecentItem()
                assertEquals(
                    "state.searchQuery should reflect the latest raw input before debounce elapses",
                    "abc",
                    midState.searchQuery,
                )
                assertEquals(
                    "repo should not have been called again before debounce elapses",
                    baseCallCount,
                    recording.capturedQueries.size,
                )

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

    @Test
    fun `onSearchChanged same text resets limit even when search needle is unchanged`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val rows = (1..(PAGE_SIZE + 10)).map { i -> makeReport("r$i", "s1", "u1") }
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { limit -> rows.take(limit) },
                totalCount = PAGE_SIZE + 10,
            )

            vm.state.test {
                advanceUntilIdle()
                expectMostRecentItem()

                vm.onLoadMore()
                advanceUntilIdle()
                val expanded = expectMostRecentItem()
                assertEquals(PAGE_SIZE + 10, expanded.reports.size)

                vm.onSearchChanged("")
                advanceUntilIdle()

                val afterSameText = expectMostRecentItem()
                assertEquals(
                    "limit must reset to PAGE_SIZE even when the search text did not change",
                    PAGE_SIZE,
                    afterSameText.reports.size,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Null-user path
    // ---------------------------------------------------------------------------

    @Test
    fun `null user yields empty reports and isLoading false`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModelWith(
                userId = null,
                rowsByLimit = { emptyList() },
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(emptyList<Report>(), settled.reports)
                assertEquals(0, settled.totalReports)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Total reports surfaced in state
    // ---------------------------------------------------------------------------

    @Test
    fun `state totalReports reflects repository total count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val expectedCount = 42
            val vm = viewModelWith(
                userId = "u1",
                rowsByLimit = { emptyList() },
                totalCount = expectedCount,
            )

            vm.state.test {
                advanceUntilIdle()
                val settled = expectMostRecentItem()
                assertFalse(settled.isLoading)
                assertEquals(expectedCount, settled.totalReports)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------------

    private fun viewModelWith(
        userId: String?,
        rowsByLimit: (Int) -> List<Report>,
        totalCount: Int = 0,
    ): RecordsViewModel {
        val reportRepo = LambdaReportRepository(rowsByLimit, totalCount)
        val authRepo = FakeAuthRepository(userId)
        val useCase = ObserveReportsUseCase(authRepo, reportRepo)
        return RecordsViewModel(useCase)
    }

    private fun viewModelWithRecording(
        reportRepo: RecordingReportRepository,
        userId: String = "u1",
    ): RecordsViewModel {
        val authRepo = FakeAuthRepository(userId)
        val useCase = ObserveReportsUseCase(authRepo, reportRepo)
        return RecordsViewModel(useCase)
    }
}

// ---------------------------------------------------------------------------
// Recording fake report repository
// ---------------------------------------------------------------------------

private class RecordingReportRepository(
    private val rowsByLimit: (Int) -> List<Report> = { emptyList() },
    private val totalCount: Int = 0,
) : ReportRepository {
    val capturedQueries = mutableListOf<String>()

    override suspend fun insert(report: Report) = Unit
    override fun observeForSession(sessionId: String, userId: String, limit: Int, offset: Int): Flow<List<Report>> =
        flowOf(emptyList())
    override fun observeCountForSession(sessionId: String, userId: String): Flow<Int> = flowOf(0)
    override fun observeAll(userId: String, limit: Int, offset: Int): Flow<List<Report>> = flowOf(emptyList())
    override fun observeAllCount(userId: String): Flow<Int> = flowOf(0)

    override fun observeFiltered(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<Report>> {
        capturedQueries += query
        return flowOf(rowsByLimit(limit))
    }

    override fun observeFilteredCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> = flowOf(totalCount)

    override suspend fun getById(reportId: String): Report? = null
    override suspend fun getReportsPendingSync(userId: String): List<Report> = emptyList()
    override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) = Unit
}

// ---------------------------------------------------------------------------
// Controllable fake report repository
// ---------------------------------------------------------------------------

private class LambdaReportRepository(
    private val rowsByLimit: (Int) -> List<Report>,
    private val totalCount: Int = 0,
) : ReportRepository {
    override suspend fun insert(report: Report) = Unit
    override fun observeForSession(sessionId: String, userId: String, limit: Int, offset: Int): Flow<List<Report>> =
        flowOf(emptyList())
    override fun observeCountForSession(sessionId: String, userId: String): Flow<Int> = flowOf(0)
    override fun observeAll(userId: String, limit: Int, offset: Int): Flow<List<Report>> = flowOf(emptyList())
    override fun observeAllCount(userId: String): Flow<Int> = flowOf(0)

    override fun observeFiltered(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<Report>> = flowOf(rowsByLimit(limit))

    override fun observeFilteredCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> = flowOf(totalCount)

    override suspend fun getById(reportId: String): Report? = null
    override suspend fun getReportsPendingSync(userId: String): List<Report> = emptyList()
    override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) = Unit
}

// ---------------------------------------------------------------------------
// Model helpers
// ---------------------------------------------------------------------------

private fun makeReport(id: String, sessionId: String, userId: String): Report =
    Report(
        id = id,
        sessionId = sessionId,
        userId = userId,
        reportType = ReportType.SESSION,
        generatedAt = Instant.ofEpochMilli(1_000L),
        totalSamples = 1,
        totalEggsConfirmed = 0,
        positiveSpecies = emptyList(),
        lpfPerSpecies = emptyMap(),
        csvFilePath = "/path/$id.csv",
        pdfFilePath = "/path/$id.pdf",
        supabaseStatus = ReportSyncStatus.SYNCED,
    )
