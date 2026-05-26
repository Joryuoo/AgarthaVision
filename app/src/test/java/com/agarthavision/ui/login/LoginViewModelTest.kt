package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.domain.usecase.auth.HasActiveSessionUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signInUseCase: SignInUseCase = mock()
    private val hasActiveSessionUseCase: HasActiveSessionUseCase = mock()

    @Test
    fun `cold start with persisted session emits NavigateToCapture`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(true)
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)

            viewModel.events.test {
                advanceUntilIdle()
                assertEquals(LoginEvent.NavigateToCapture, awaitItem())
            }
        }

    @Test
    fun `cold start without session clears isCheckingSession`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(false)
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)

            advanceUntilIdle()
            assertFalse(viewModel.state.value.isCheckingSession)
        }

    @Test
    fun `submit with malformed email flags emailError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(false)
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)
            advanceUntilIdle()

            viewModel.onEmailChanged("not-an-email")
            viewModel.onPasswordChanged("password123")
            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.emailError)
            assertFalse(viewModel.state.value.passwordError)
            verify(signInUseCase, org.mockito.kotlin.never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `submit with blank password flags passwordError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(false)
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)
            advanceUntilIdle()

            viewModel.onEmailChanged("user@example.com")
            viewModel.onPasswordChanged("")
            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.passwordError)
            verify(signInUseCase, org.mockito.kotlin.never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `submit with valid credentials emits NavigateToCapture`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(false)
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("secret123")
                viewModel.onSubmit()
                advanceUntilIdle()
                assertEquals(LoginEvent.NavigateToCapture, awaitItem())
            }
            assertFalse(viewModel.state.value.isSubmitting)
        }

    @Test
    fun `submit failure emits ShowLoginError with exception message`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(hasActiveSessionUseCase.invoke()).thenReturn(false)
            whenever(signInUseCase.invoke("user@example.com", "wrong"))
                .thenReturn(Result.failure(IllegalStateException("Invalid credentials")))
            val viewModel = LoginViewModel(hasActiveSessionUseCase, signInUseCase)
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("wrong")
                viewModel.onSubmit()
                advanceUntilIdle()
                assertEquals(LoginEvent.ShowLoginError("Invalid credentials"), awaitItem())
            }
            assertFalse(viewModel.state.value.isSubmitting)
        }
}
