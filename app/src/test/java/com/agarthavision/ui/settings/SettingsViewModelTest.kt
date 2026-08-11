package com.agarthavision.ui.settings

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.auth.SignOutUseCase
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val identityFlow = MutableStateFlow<LocalIdentity?>(
        LocalIdentity(userId = "user-1", email = "medtech@example.com"),
    )
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(identityFlow)
        }
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val pendingCountsFlow = MutableStateFlow(PendingSyncCounts(0, 0, 0, 0))
    private val observePendingSyncCountsUseCase: ObservePendingSyncCountsUseCase =
        mock<ObservePendingSyncCountsUseCase>().also {
            whenever(it.invoke(any())).thenReturn(pendingCountsFlow)
        }
    private val themeModeFlow = MutableStateFlow(ThemeMode.LIGHT)
    private val observeThemeModeUseCase: ObserveThemeModeUseCase = mock<ObserveThemeModeUseCase>().also {
        whenever(it.invoke()).thenReturn(themeModeFlow)
    }
    private val setThemeModeUseCase: SetThemeModeUseCase = mock()
    private val syncPendingDataUseCase: SyncPendingDataUseCase = mock<SyncPendingDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(SyncSummary.Skipped)) }
    }
    private val signOutUseCase: SignOutUseCase = mock()

    private fun viewModel() = SettingsViewModel(
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        connectivityObserver = connectivityObserver,
        observePendingSyncCountsUseCase = observePendingSyncCountsUseCase,
        observeThemeModeUseCase = observeThemeModeUseCase,
        setThemeModeUseCase = setThemeModeUseCase,
        syncPendingDataUseCase = syncPendingDataUseCase,
        signOutUseCase = signOutUseCase,
    )

    @Test
    fun `initial state reflects signed-in identity`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertTrue(snapshot.isSignedIn)
            assertFalse(snapshot.isOffline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renders never-signed-in state with empty pending counts`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            identityFlow.value = null
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertFalse(snapshot.isSignedIn)
                assertTrue(snapshot.pendingSyncCounts.allSynced)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onToggleTheme persists dark mode from light`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
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

        verify(setThemeModeUseCase).invoke(ThemeMode.DARK)
    }

    @Test
    fun `onSyncNow invokes SyncPendingDataUseCase when signed in and online`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                cancelAndIgnoreRemainingEvents()
            }

            vm.onSyncNow()
            advanceUntilIdle()

            verify(syncPendingDataUseCase).invoke()
        }

    @Test
    fun `onSyncNow does nothing when signed out`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        identityFlow.value = null
        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }

        vm.onSyncNow()
        advanceUntilIdle()

        verify(syncPendingDataUseCase, never()).invoke()
    }

    @Test
    fun `onSignOut emits SignedOut on success`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        whenever(signOutUseCase.invoke()).thenReturn(Result.success(Unit))
        val vm = viewModel()

        vm.eventFlow.test {
            vm.onSignOut()
            assertTrue(awaitItem() is SettingsEvent.SignedOut)
        }
    }

    @Test
    fun `onSignOut emits SignOutBlocked when the use case fails`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signOutUseCase.invoke())
                .thenReturn(Result.failure(IllegalStateException("End the active session before signing out.")))
            val vm = viewModel()

            vm.eventFlow.test {
                vm.onSignOut()
                val event = awaitItem()
                assertTrue(event is SettingsEvent.SignOutBlocked)
            }
        }
}
