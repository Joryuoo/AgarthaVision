package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signInUseCase: SignInUseCase = mock()
    private val authRepository: AuthRepository = mock<AuthRepository>().also {
        runBlocking { whenever(it.currentLocalUserId()).thenReturn("user-1") }
    }
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.currentlyOnline()).thenReturn(true)
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val claimLocalDataUseCase: ClaimLocalDataUseCase = mock<ClaimLocalDataUseCase>().also {
        runBlocking { whenever(it.invoke("user-1", null)).thenReturn(Result.success(0)) }
    }
    private val syncPendingDataUseCase: SyncPendingDataUseCase = mock<SyncPendingDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(SyncSummary.Skipped)) }
    }

    private fun viewModel() = LoginViewModel(
        signInUseCase = signInUseCase,
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        claimLocalDataUseCase = claimLocalDataUseCase,
        syncPendingDataUseCase = syncPendingDataUseCase,
    )

    @Test
    fun `submit with malformed email flags emailError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()

            viewModel.onEmailChanged("not-an-email")
            viewModel.onPasswordChanged("password123")
            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.emailError)
            assertFalse(viewModel.state.value.passwordError)
            verify(signInUseCase, never()).invoke(any(), any())
        }

    @Test
    fun `submit with blank password flags passwordError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()

            viewModel.onEmailChanged("user@example.com")
            viewModel.onPasswordChanged("")
            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.passwordError)
            verify(signInUseCase, never()).invoke(any(), any())
        }

    @Test
    fun `submit with valid credentials emits NavigateBack`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("secret123")
                viewModel.onSubmit()
                advanceUntilIdle()
                assertEquals(LoginEvent.NavigateBack, awaitItem())
            }
            assertFalse(viewModel.state.value.isSubmitting)
        }

    @Test
    fun `submit failure emits ShowLoginError with exception message`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "wrong"))
                .thenReturn(Result.failure(IllegalStateException("Invalid credentials")))
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("wrong")
                viewModel.onSubmit()
                advanceUntilIdle()
                assertEquals(LoginEvent.ShowLoginError("Invalid credentials"), awaitItem())
            }
            assertFalse(viewModel.state.value.isSubmitting)
        }

    @Test
    fun `submit success claims local data and triggers pending sync`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("secret123")
                viewModel.onSubmit()
                advanceUntilIdle()
                awaitItem()
            }

            verify(claimLocalDataUseCase).invoke("user-1", null)
            verify(syncPendingDataUseCase).invoke()
        }
}
