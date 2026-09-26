package com.agarthavision.ui.dashboard

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.sync.InitialFetchStateStore
import androidx.lifecycle.SavedStateHandle
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.AgreementBreakdown
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.FindingsResult
import com.agarthavision.domain.model.HomeKpis
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.KpiMetric
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Ratio
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.model.windows
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.home.NeedsAttention
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import com.agarthavision.domain.usecase.home.ObserveHomeKpisUseCase
import com.agarthavision.domain.usecase.home.ObserveNeedsAttentionUseCase
import com.agarthavision.domain.usecase.home.ObserveRecentActivityUseCase
import com.agarthavision.domain.usecase.home.ObserveSessionListUseCase
import com.agarthavision.domain.usecase.home.SessionListResult
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.FetchSummary
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import com.agarthavision.util.MainDispatcherRule
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.mockito.kotlin.anyOrNull
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
        whenever(it.observePendingCount(any())).thenReturn(flowOf(0))
        runBlocking { whenever(it.getSamplesPendingSyncIncludingDeleted(any())).thenReturn(emptyList()) }
    }
    private val patientRepository: PatientRepository = mock<PatientRepository>().also {
        whenever(
            it.observePatientCount(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(flowOf(0))
    }
    private val themeModeFlow = MutableStateFlow(ThemeMode.LIGHT)
    private val observeThemeModeUseCase: ObserveThemeModeUseCase = mock<ObserveThemeModeUseCase>().also {
        whenever(it.invoke()).thenReturn(themeModeFlow)
    }
    private val setThemeModeUseCase: SetThemeModeUseCase = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), CLINICAL_ZONE)
    private val observeNeedsAttentionUseCase: ObserveNeedsAttentionUseCase = mock<ObserveNeedsAttentionUseCase>().also {
        whenever(it.invoke(any(), anyOrNull())).thenReturn(flowOf(NeedsAttention(0, 0, 0)))
    }
    private val defaultKpis = HomeKpis(
        sessions = KpiMetric(0, 0, emptyList()),
        patientsInSessions = 0,
        positiveRate = KpiMetric(Ratio(0, 0), Ratio(0, 0), emptyList()),
        toReview = KpiMetric(0, 0, emptyList()),
        verifiedInPeriod = 0,
        aiAgreement = KpiMetric(Ratio(0, 0), Ratio(0, 0), emptyList()),
        aiBreakdown = AgreementBreakdown(0, 0, 0, 0),
    )
    private val observeHomeKpisUseCase: ObserveHomeKpisUseCase = mock<ObserveHomeKpisUseCase>().also {
        whenever(it.invoke(any(), any())).thenReturn(flowOf(defaultKpis))
    }
    private val defaultFindings = FindingsResult(
        species = listOf(
            SpeciesFinding(
                name = "Ascaris lumbricoides",
                count = 5,
                ratio = 0.5f,
                formattedPercentage = "50%",
            ),
        ),
        positiveSmearsCount = 3,
    )
    private val observeFindingsUseCase: ObserveFindingsUseCase = mock<ObserveFindingsUseCase>().also {
        whenever(it.invoke(any(), any(), anyOrNull())).thenReturn(flowOf(defaultFindings))
    }
    private val observeSessionListUseCase: ObserveSessionListUseCase = mock<ObserveSessionListUseCase>().also {
        whenever(it.invoke(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )
    }
    private val observeRecentActivityUseCase: ObserveRecentActivityUseCase =
        mock<ObserveRecentActivityUseCase>().also {
            whenever(it.invoke(any(), any())).thenReturn(flowOf(emptyList()))
        }

    private fun samplePatient(id: String) = Patient(
        id = id,
        lastname = "Rizal",
        firstname = "Jose",
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1990, 1, 1),
        psgcBarangayCode = "0102801001",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun sampleSession(id: String) = Session(
        id = id,
        patientId = "patient-1",
        userId = "user-1",
        deviceId = "dev-1",
        startedAt = 0L,
        label = "Smear 1",
    )

    private fun sampleSummary(sessionId: String) = SessionSummary(
        session = sampleSession(sessionId),
        patient = samplePatient("patient-1"),
        totalFrames = 10,
        framesToReview = 2,
        isPositive = false,
        lastActivityAt = 1_000L,
    )

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = DashboardViewModel(
        savedStateHandle = savedStateHandle,
        clock = clock,
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        connectivityObserver = connectivityObserver,
        syncPendingDataUseCase = syncPendingDataUseCase,
        fetchRemoteDataUseCase = fetchRemoteDataUseCase,
        initialFetchStateStore = initialFetchStateStore,
        sessionManager = sessionManager,
        sessionRepository = sessionRepository,
        sampleRepository = sampleRepository,
        patientRepository = patientRepository,
        observeThemeModeUseCase = observeThemeModeUseCase,
        setThemeModeUseCase = setThemeModeUseCase,
        observeNeedsAttentionUseCase = observeNeedsAttentionUseCase,
        observeHomeKpisUseCase = observeHomeKpisUseCase,
        observeFindingsUseCase = observeFindingsUseCase,
        observeSessionListUseCase = observeSessionListUseCase,
        observeRecentActivityUseCase = observeRecentActivityUseCase,
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
        whenever(
            patientRepository.observePatientCount(
                any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            ),
        ).thenReturn(flowOf(3))
        whenever(sampleRepository.observePendingCount(any())).thenReturn(flowOf(4))

        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }

            assertTrue(snapshot.isSignedIn)
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

    @Test
    fun `uiState stays loading until the identity flow emits`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val identityFlow = MutableSharedFlow<LocalIdentity?>(replay = 1)
            whenever(observeLocalIdentityUseCase.invoke()).thenReturn(identityFlow)
            whenever(
                patientRepository.observePatientCount(
                    any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
                ),
            ).thenReturn(flowOf(3))

            val vm = viewModel()
            val collectJob = launch { vm.uiState.collect { } }
            advanceUntilIdle()

            assertTrue("should stay loading until identity is known", vm.uiState.value.isLoading)

            identityFlow.emit(LocalIdentity(userId = "user-1", email = "user@example.com"))
            advanceUntilIdle()

            val snapshot = vm.uiState.value
            collectJob.cancel()

            assertFalse(snapshot.isLoading)
            assertTrue(snapshot.isSignedIn)
            assertEquals("3", snapshot.kpis.patientsCount)
        }

    @Test
    fun `a genuinely signed-out identity settles with isLoading false and zero KPIs`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(observeLocalIdentityUseCase.invoke()).thenReturn(flowOf(null))

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }

                assertFalse(snapshot.isSignedIn)
                assertEquals("0", snapshot.kpis.patientsCount)
                assertEquals("0", snapshot.kpis.sessionsCount)
                assertEquals("0", snapshot.kpis.samplesCount)
                assertEquals("0", snapshot.kpis.pendingCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `period selection survives on same SavedStateHandle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val savedStateHandle = SavedStateHandle()
            val vm1 = viewModel(savedStateHandle)
            vm1.onPeriodSelected(HomePeriod.LAST_30_DAYS)

            val vm2 = viewModel(savedStateHandle)
            vm2.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertEquals(HomePeriod.LAST_30_DAYS, snapshot.period)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `kpiTiles formats HomeKpis metrics and trends correctly`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val sampleKpis = HomeKpis(
                sessions = KpiMetric(10, 5, listOf(1.0, 2.0)),
                patientsInSessions = 4,
                positiveRate = KpiMetric(Ratio(2, 8), Ratio(1, 4), listOf(0.25)),
                toReview = KpiMetric(3, 1, listOf(3.0)),
                verifiedInPeriod = 15,
                aiAgreement = KpiMetric(Ratio(18, 20), Ratio(9, 10), listOf(0.9)),
                aiBreakdown = AgreementBreakdown(
                    confirmed = 18,
                    wrongClass = 1,
                    boxIncorrect = 1,
                    falsePositive = 0,
                ),
            )
            whenever(observeHomeKpisUseCase.invoke(any(), any())).thenReturn(flowOf(sampleKpis))

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading || snapshot.kpiTiles.isEmpty()) {
                    snapshot = awaitItem()
                }

                val tiles = snapshot.kpiTiles
                assertEquals(4, tiles.size)

                assertEquals("10", tiles[0].value)
                assertEquals("4 patients", tiles[0].subtitle)
                assertEquals("+5 day", tiles[0].changeText)
                assertEquals(listOf(1.0, 2.0), tiles[0].sparkline)

                assertEquals("25%", tiles[1].value)
                assertEquals("2 of 8 smears", tiles[1].subtitle)
                assertEquals("Flat", tiles[1].changeText)

                assertEquals("3", tiles[2].value)
                assertEquals("15 verified today", tiles[2].subtitle)
                assertEquals("+2 day", tiles[2].changeText)

                assertEquals("90%", tiles[3].value)
                assertEquals("2 of 20 corrected by you", tiles[3].subtitle)
                assertEquals("Flat", tiles[3].changeText)

                assertEquals(18, snapshot.aiBreakdown.confirmed)
                assertEquals(2, snapshot.aiBreakdown.corrected)
                assertEquals("Findings · today", snapshot.findingsTitle)
                assertEquals(3, snapshot.positiveSmearsCount)
                assertEquals(1, snapshot.topSpecies.size)
                assertEquals("Ascaris lumbricoides", snapshot.topSpecies[0].name)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onPeriodSelected re-invokes use cases with 30-day windows`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }

                vm.onPeriodSelected(HomePeriod.LAST_30_DAYS)
                snapshot = awaitItem()
                while (snapshot.period != HomePeriod.LAST_30_DAYS) {
                    snapshot = awaitItem()
                }

                assertEquals(HomePeriod.LAST_30_DAYS, snapshot.period)
                assertEquals("Findings · last 30 days", snapshot.findingsTitle)

                val expectedWindow = HomePeriod.LAST_30_DAYS.windows(clock.instant(), CLINICAL_ZONE)
                verify(observeHomeKpisUseCase, atLeastOnce()).invoke(
                    eq("user-1"),
                    argThat { current.startMillis == expectedWindow.current.startMillis },
                )
                verify(observeFindingsUseCase, atLeastOnce()).invoke(
                    eq("user-1"),
                    argThat { startMillis == expectedWindow.current.startMillis },
                    anyOrNull(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `active session is excluded from recentSessions and list is capped at 5`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val activeEntity = SessionEntity(
                sessionId = "s-active",
                userId = "user-1",
                patientId = "p-1",
                deviceId = "d-1",
                startedAt = 1000L,
                label = "Active Session",
            )
            whenever(sessionManager.state).thenReturn(
                MutableStateFlow(SessionState.Active(activeEntity, Instant.ofEpochMilli(1000L))),
            )
            val summaries = (1..6).map { sampleSummary("s-$it") } + sampleSummary("s-active")
            whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
                flowOf(SessionListResult(summaries, 7)),
            )

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading || snapshot.recentSessions.isEmpty()) {
                    snapshot = awaitItem()
                }
                assertEquals(5, snapshot.recentSessions.size)
                assertFalse(snapshot.recentSessions.any { it.session.id == "s-active" })
                assertEquals(
                    listOf("s-1", "s-2", "s-3", "s-4", "s-5"),
                    snapshot.recentSessions.map { it.session.id },
                )
                assertTrue(snapshot.hasAnySession)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `single active session gives empty recentSessions row and hasAnySession is true`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val activeEntity = SessionEntity(
                sessionId = "s-active",
                userId = "user-1",
                patientId = "p-1",
                deviceId = "d-1",
                startedAt = 1000L,
                label = "Active Session",
            )
            whenever(sessionManager.state).thenReturn(
                MutableStateFlow(SessionState.Active(activeEntity, Instant.ofEpochMilli(1000L))),
            )
            whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
                flowOf(SessionListResult(listOf(sampleSummary("s-active")), 1)),
            )

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertTrue(snapshot.recentSessions.isEmpty())
                assertTrue(snapshot.hasAnySession)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState recentActivity reflects what the use case emits`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val activity = listOf(
                ActivityItem.PatientAdded(
                    patientId = "p-1",
                    maskedName = "R.*, J*.",
                    occurredAt = 5_000L,
                ),
            )
            whenever(observeRecentActivityUseCase.invoke(any(), any())).thenReturn(flowOf(activity))

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading || snapshot.recentActivity.isEmpty()) {
                    snapshot = awaitItem()
                }
                assertEquals(activity, snapshot.recentActivity)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no sessions gives empty recentSessions and hasAnySession is false`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(sessionManager.state).thenReturn(MutableStateFlow(SessionState.Idle))
            whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
                flowOf(SessionListResult(emptyList(), 0)),
            )

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertTrue(snapshot.recentSessions.isEmpty())
                assertFalse(snapshot.hasAnySession)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
