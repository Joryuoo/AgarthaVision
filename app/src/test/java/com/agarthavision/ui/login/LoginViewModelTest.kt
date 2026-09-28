package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.FetchSummary
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
import org.mockito.kotlin.inOrder
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
    private val syncPendingDataUseCase: SyncPendingDataUseCase = mock<SyncPendingDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(SyncSummary.Skipped)) }
    }
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase = mock<FetchRemoteDataUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(FetchSummary.Skipped)) }
    }

    private fun viewModel() = LoginViewModel(
        signInUseCase = signInUseCase,
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        syncPendingDataUseCase = syncPendingDataUseCase,
        fetchRemoteDataUseCase = fetchRemoteDataUseCase,
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
    fun `submit success triggers pending sync then the remote fetch`() =
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

            verify(syncPendingDataUseCase).invoke()
            verify(fetchRemoteDataUseCase).invoke()
        }

    @Test
    fun `post-login sequence is push then pull in that order`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("secret123")
                viewModel.onSubmit()
                advanceUntilIdle()
                awaitItem() // consume NavigateBack
            }

            // There is no claim step any more: login is mandatory on first run, so nothing
            // can have been created without an owner for a claim to adopt.
            val order = inOrder(syncPendingDataUseCase, fetchRemoteDataUseCase)
            order.verify(syncPendingDataUseCase).invoke()
            order.verify(fetchRemoteDataUseCase).invoke()
        }

    @Test
    fun `fetchRemoteDataUseCase is not called when sign-in fails`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "wrong"))
                .thenReturn(Result.failure(IllegalStateException("Invalid credentials")))
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("wrong")
                viewModel.onSubmit()
                advanceUntilIdle()
                awaitItem() // consume ShowLoginError
            }

            verify(fetchRemoteDataUseCase, never()).invoke()
        }
}
