package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.core.auth.BiometricPromptManager
import com.agarthavision.core.auth.BiometricStatus
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.BiometricLockRepository
import com.agarthavision.domain.usecase.auth.CompleteSignInUseCase
import com.agarthavision.domain.usecase.auth.ObserveSignedOutNoticeUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: the view model logs a failed post-login pull through android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signInUseCase: SignInUseCase = mock()
    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.currentlyOnline()).thenReturn(true)
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val completeSignInUseCase: CompleteSignInUseCase = mock<CompleteSignInUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(Unit)) }
    }

    private val signedOutNotice = MutableStateFlow<SignedOutNotice?>(null)
    private val observeSignedOutNoticeUseCase: ObserveSignedOutNoticeUseCase =
        mock<ObserveSignedOutNoticeUseCase>().also { whenever(it.invoke()).thenReturn(signedOutNotice) }

    private val biometricPromptManager: BiometricPromptManager = mock<BiometricPromptManager>().also {
        whenever(it.getBiometricStatus()).thenReturn(BiometricStatus.NOT_AVAILABLE)
    }
    private val biometricLockRepository: BiometricLockRepository = mock<BiometricLockRepository>().also {
        whenever(it.isBiometricLockEnabled).thenReturn(MutableStateFlow(false))
    }

    private fun viewModel() = LoginViewModel(
        signInUseCase = signInUseCase,
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        completeSignInUseCase = completeSignInUseCase,
        observeSignedOutNoticeUseCase = observeSignedOutNoticeUseCase,
        biometricPromptManager = biometricPromptManager,
        biometricLockRepository = biometricLockRepository,
    )

    @Test
    fun `a sign-out by the server reaches the screen with its count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // 14zcqntjph8: the wipe may have happened in a background sync with no screen open,
            // so the reason is read back from the store, not passed along a navigation.
            signedOutNotice.value = SignedOutNotice(unsyncedKept = 2)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SignedOutNotice(unsyncedKept = 2), viewModel.state.value.signedOutNotice)

            signedOutNotice.value = null
            advanceUntilIdle()

            assertEquals(null, viewModel.state.value.signedOutNotice)
        }

    @Test
    fun `submit with malformed email flags emailError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("not-an-email")
                viewModel.onPasswordChanged("password123")
                viewModel.onSubmit()
                advanceUntilIdle()

                assertEquals(LoginEvent.ShowLoginError("Enter a valid email address."), awaitItem())
            }

            assertTrue(viewModel.state.value.emailError)
            assertFalse(viewModel.state.value.passwordError)
            verify(signInUseCase, never()).invoke(any(), any())
        }

    @Test
    fun `submit with blank password flags passwordError and skips signIn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onEmailChanged("user@example.com")
                viewModel.onPasswordChanged("")
                viewModel.onSubmit()
                advanceUntilIdle()

                assertEquals(LoginEvent.ShowLoginError("Password is required."), awaitItem())
            }

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
    fun `submit success completes sign-in then emits NavigateBack`() =
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

            verify(completeSignInUseCase).invoke()
        }

    @Test
    fun `a failed completeSignIn still navigates`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            whenever(completeSignInUseCase.invoke())
                .thenReturn(Result.failure(IllegalStateException("pull failed")))
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
    fun `completeSignIn is not called when sign-in fails`() =
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

            verify(completeSignInUseCase, never()).invoke()
            assertEquals(null, viewModel.state.value.stage)
        }

    @Test
    fun `stage goes SIGNING_IN then DOWNLOADING_PATIENTS then null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123"))
                .thenReturn(Result.success(Unit))
            val viewModel = viewModel()
            // Unconfined, so every state update is seen and StateFlow conflation hides nothing.
            val seen = mutableListOf<LoginStage?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect { seen += it.stage }
            }

            viewModel.onEmailChanged("user@example.com")
            viewModel.onPasswordChanged("secret123")
            viewModel.onSubmit()
            advanceUntilIdle()

            val expected = listOf(null, LoginStage.SIGNING_IN, LoginStage.DOWNLOADING_PATIENTS, null)
            assertEquals(expected, seen.distinctUntilChangedList())
        }

    private fun <T> List<T>.distinctUntilChangedList(): List<T> =
        filterIndexed { i, v -> i == 0 || v != this[i - 1] }
}
