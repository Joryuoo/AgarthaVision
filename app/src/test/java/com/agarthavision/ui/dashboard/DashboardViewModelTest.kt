package com.agarthavision.ui.dashboard

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.FetchSummary
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")),
            )
        }
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val syncPendingDataUseCase: SyncPendingDataUseCase = mock<SyncPendingDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(SyncSummary.Skipped)) }
    }
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase = mock<FetchRemoteDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(FetchSummary.Skipped)) }
    }
    private val initialFetchStateStore: InitialFetchStateStore = mock<InitialFetchStateStore>().also {
        whenever(it.observeCompleted(any())).thenReturn(MutableStateFlow(true))
    }
    private val sessionManager: SessionManager = mock<SessionManager>().also {
        whenever(it.state).thenReturn(MutableStateFlow(SessionState.Idle))
    }
    private val sessionRepository: SessionRepository = mock<SessionRepository>().also {
        whenever(it.observeAllSessions(any())).thenReturn(flowOf(emptyList()))
    }
    private val sampleRepository: SampleRepository = mock<SampleRepository>().also {
        whenever(it.observeAllSamples(any())).thenReturn(flowOf(emptyList()))
        whenever(it.observeSamplesForSession(any(), any())).thenReturn(flowOf(emptyList()))
        runBlocking { whenever(it.getSamplesPendingSyncIncludingDeleted(any())).thenReturn(emptyList()) }
    }
    private val detectionRepository: DetectionRepository = mock<DetectionRepository>().also {
        whenever(it.observeConfirmedEggCountsSince(any(), any())).thenReturn(flowOf(emptyList()))
    }
    private val sampleDao: SampleDao = mock<SampleDao>().also {
        whenever(it.observePendingCount(any())).thenReturn(flowOf(0))
    }
    private val patientRepository: PatientRepository = mock<PatientRepository>().also {
        whenever(it.observePatientCount(any(), any())).thenReturn(flowOf(0))
    }
    private val themeModeFlow = MutableStateFlow(ThemeMode.LIGHT)
    private val observeThemeModeUseCase: ObserveThemeModeUseCase = mock<ObserveThemeModeUseCase>().also {
        whenever(it.invoke()).thenReturn(themeModeFlow)
    }
    private val setThemeModeUseCase: SetThemeModeUseCase = mock()

    private fun viewModel() = DashboardViewModel(
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        connectivityObserver = connectivityObserver,
        syncPendingDataUseCase = syncPendingDataUseCase,
        fetchRemoteDataUseCase = fetchRemoteDataUseCase,
        initialFetchStateStore = initialFetchStateStore,
        sessionManager = sessionManager,
        sessionRepository = sessionRepository,
        sampleRepository = sampleRepository,
        sampleDao = sampleDao,
        patientRepository = patientRepository,
        detectionRepository = detectionRepository,
        observeThemeModeUseCase = observeThemeModeUseCase,
        setThemeModeUseCase = setThemeModeUseCase,
    )

    @Test
    fun `initial state reflects observed light mode`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertFalse(snapshot.isDarkMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleTheme flips light state to dark and persists it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(setThemeModeUseCase.invoke(ThemeMode.DARK)).thenReturn(Result.success(Unit))
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                cancelAndIgnoreRemainingEvents()
            }

            vm.onToggleTheme()
            advanceUntilIdle()

            verify(setThemeModeUseCase).invoke(eq(ThemeMode.DARK))
        }

    // ── allSynced gating ────────────────────────────────────────────────────

    @Test
    fun `allSynced is false when initialFetchDone is false even with empty unsynced count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // initialFetchDone = false → allSynced must be false once the signed-in path settles
            whenever(initialFetchStateStore.observeCompleted(any())).thenReturn(MutableStateFlow(false))
            val vm = viewModel()

            // Subscribe to activate SharingStarted.WhileSubscribed, then let the scheduler drain
            // all pending tasks (identity load, flatMapLatest switch, combine re-emit).
            val collectJob = launch { vm.uiState.collect { } }
            advanceUntilIdle()

            val snapshot = vm.uiState.value
            collectJob.cancel()

            assertTrue("snapshot must be signed-in for allSynced to be meaningful", snapshot.isSignedIn)
            assertFalse("allSynced should be false when initialFetchDone=false", snapshot.allSynced)
        }

    @Test
    fun `allSynced is true when initialFetchDone is true and no unsynced samples`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Default mock: initialFetchDone = true, observeAllSamples returns empty list
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertTrue(
                    "allSynced should be true when initialFetchDone=true and zero unsynced samples",
                    snapshot.allSynced,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `allSynced is true when userId is null (signed-out path)`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Signed-out identity → pendingAndSyncFlow emits allSynced=true unconditionally
            whenever(observeLocalIdentityUseCase.invoke()).thenReturn(flowOf(null))
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertTrue(
                    "allSynced should be true when signed out (no userId)",
                    snapshot.allSynced,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onToggleTheme flips dark state back to light`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        themeModeFlow.value = ThemeMode.DARK
        whenever(setThemeModeUseCase.invoke(ThemeMode.LIGHT)).thenReturn(Result.success(Unit))
        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertTrue(snapshot.isDarkMode)
            cancelAndIgnoreRemainingEvents()
        }

        vm.onToggleTheme()
        advanceUntilIdle()

        verify(setThemeModeUseCase).invoke(eq(ThemeMode.LIGHT))
    }

    @Test
    fun `every KPI tile is a row count`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        // The bar PB-23 sets: if a tile cannot be explained by pointing at data, it does not
        // ship. `verifiedRatio` returned "100%" whenever any sample existed - a constant
        // wearing a percent sign - and `eggsAvgStatus` read "Elevated" off `totalSamples > 100`,
        // a sample count dressed as a clinical intensity on a screen used during validation.
        whenever(patientRepository.observePatientCount(any(), any())).thenReturn(flowOf(3))
        whenever(sampleDao.observePendingCount(any())).thenReturn(flowOf(4))

        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            // The KPI flow hangs off userIdFlow, whose stateIn seed is null, and a null
            // identity yields KpiState() zeros by design (ADR-007). The first non-loading
            // frame is therefore the signed-out one, so waiting only on isLoading asserts
            // against that transient rather than against the counts.
            while (snapshot.isLoading || snapshot.kpis.patientsCount == "0") {
                snapshot = awaitItem()
            }

            assertEquals("3", snapshot.kpis.patientsCount)
            assertEquals("4", snapshot.kpis.pendingCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a fresh account reads zero everywhere, with no percentage it cannot justify`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }

                assertEquals("0", snapshot.kpis.patientsCount)
                assertEquals("0", snapshot.kpis.sessionsCount)
                assertEquals("0", snapshot.kpis.samplesCount)
                assertEquals("0", snapshot.kpis.pendingCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
