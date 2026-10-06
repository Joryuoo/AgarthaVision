package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.core.auth.BiometricPromptManager
import com.agarthavision.core.auth.BiometricStatus
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.BiometricLockRepository
import com.agarthavision.domain.usecase.auth.CompleteSignInUseCase
import com.agarthavision.domain.usecase.auth.ObserveSignedOutNoticeUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelEdgeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signInUseCase: SignInUseCase = mock()
    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.currentlyOnline()).thenReturn(true)
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val completeSignInUseCase: CompleteSignInUseCase = mock()
    private val notice = mock<ObserveSignedOutNoticeUseCase>().also {
        whenever(it.invoke()).thenReturn(MutableStateFlow(null))
    }

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
        observeSignedOutNoticeUseCase = notice,
        biometricPromptManager = biometricPromptManager,
        biometricLockRepository = biometricLockRepository,
    )

    @Test
    fun `a throwing completeSignIn still navigates and clears the stage`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123")).thenReturn(Result.success(Unit))
            runBlocking {
                whenever(completeSignInUseCase.invoke()).thenThrow(IllegalStateException("scheduler blew up"))
            }
            val vm = viewModel()
            vm.events.test {
                vm.onEmailChanged("user@example.com")
                vm.onPasswordChanged("secret123")
                vm.onSubmit()
                advanceUntilIdle()
                assertEquals(LoginEvent.NavigateBack, awaitItem())
            }
            assertFalse(vm.state.value.isSubmitting)
            assertEquals(null, vm.state.value.stage)
        }
}
