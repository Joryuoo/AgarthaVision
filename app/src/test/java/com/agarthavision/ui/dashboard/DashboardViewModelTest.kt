package com.agarthavision.ui.dashboard

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        whenever(it.observeDailyEggCountsSince(any(), any())).thenReturn(flowOf(emptyList()))
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
        sessionManager = sessionManager,
        sessionRepository = sessionRepository,
        sampleRepository = sampleRepository,
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
}
