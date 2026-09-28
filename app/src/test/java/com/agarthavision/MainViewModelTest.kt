package com.agarthavision

import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.AuthGate
import com.agarthavision.domain.usecase.auth.ResolveAuthGateUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * MainViewModel no longer depends on SessionManager (86d4byw6p) — restoring the active
 * session moved to AgarthaVisionApp.onCreate so it runs off the main thread. These tests
 * pin that authGate still resolves correctly from only the two remaining use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeThemeModeUseCase: ObserveThemeModeUseCase = mock()
    private val resolveAuthGateUseCase: ResolveAuthGateUseCase = mock()

    @Test
    fun `authGate starts Loading and resolves to Authed once the use case returns`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(observeThemeModeUseCase.invoke()).thenReturn(flowOf(ThemeMode.LIGHT))
            runBlocking { whenever(resolveAuthGateUseCase.invoke()).thenReturn(AuthGate.Authed) }

            val viewModel = MainViewModel(observeThemeModeUseCase, resolveAuthGateUseCase)

            // The splash is held on Loading until the coroutine launched in init completes.
            assertTrue(viewModel.authGate.value is AuthGate.Loading)

            advanceUntilIdle()

            assertEquals(AuthGate.Authed, viewModel.authGate.value)
        }

    @Test
    fun `authGate resolves to NeedsLogin when nobody has ever signed in`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(observeThemeModeUseCase.invoke()).thenReturn(flowOf(ThemeMode.LIGHT))
            runBlocking { whenever(resolveAuthGateUseCase.invoke()).thenReturn(AuthGate.NeedsLogin) }

            val viewModel = MainViewModel(observeThemeModeUseCase, resolveAuthGateUseCase)
            advanceUntilIdle()

            assertEquals(AuthGate.NeedsLogin, viewModel.authGate.value)
        }

    @Test
    fun `themeMode defaults to LIGHT until the persisted preference loads`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(observeThemeModeUseCase.invoke()).thenReturn(flowOf(ThemeMode.DARK))
            runBlocking { whenever(resolveAuthGateUseCase.invoke()).thenReturn(AuthGate.Authed) }

            val viewModel = MainViewModel(observeThemeModeUseCase, resolveAuthGateUseCase)

            assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)

            advanceUntilIdle()
        }
}
