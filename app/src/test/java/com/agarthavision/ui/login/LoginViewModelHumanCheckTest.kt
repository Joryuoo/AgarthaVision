package com.agarthavision.ui.login

import app.cash.turbine.test
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.HumanCheckConfig
import com.agarthavision.domain.model.HumanCheckRejectedException
import com.agarthavision.domain.model.SignedOutNotice
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Sign-in with the human check on (14zcqntnqxf). */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelHumanCheckTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signInUseCase: SignInUseCase = mock()
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.currentlyOnline()).thenReturn(true)
        whenever(it.isOnline).thenReturn(MutableStateFlow(true))
    }
    private val completeSignInUseCase: CompleteSignInUseCase = mock<CompleteSignInUseCase>().also {
        runBlocking { whenever(it.invoke()).thenReturn(Result.success(Unit)) }
    }
    private val observeSignedOutNoticeUseCase: ObserveSignedOutNoticeUseCase =
        mock<ObserveSignedOutNoticeUseCase>().also {
            whenever(it.invoke()).thenReturn(MutableStateFlow<SignedOutNotice?>(null))
        }

    private fun viewModel() = LoginViewModel(
        signInUseCase = signInUseCase,
        connectivityObserver = connectivityObserver,
        completeSignInUseCase = completeSignInUseCase,
        observeSignedOutNoticeUseCase = observeSignedOutNoticeUseCase,
        humanCheckConfig = HumanCheckConfig(siteKey = "0x4AAAAAAA-test", hostname = "example.test"),
    )

    private fun LoginViewModel.fill() {
        onEmailChanged("user@example.com")
        onPasswordChanged("secret123")
    }

    @Test
    fun `the button waits for the check and a tap before it signs nobody in`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            viewModel.fill()

            assertFalse(viewModel.state.value.canSubmit)
            viewModel.onSubmit()
            advanceUntilIdle()

            verify(signInUseCase, never()).invoke(any(), any(), anyOrNull())
        }

    @Test
    fun `the token goes with the sign-in`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        whenever(signInUseCase.invoke("user@example.com", "secret123", "token-1")).thenReturn(Result.success(Unit))
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.onHumanCheckToken("token-1")

        assertTrue(viewModel.state.value.canSubmit)
        viewModel.events.test {
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(LoginEvent.NavigateBack, awaitItem())
        }
        verify(signInUseCase).invoke("user@example.com", "secret123", "token-1")
    }

    @Test
    fun `a refused check says so, not that the password was wrong, and asks for a fresh check`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(signInUseCase.invoke("user@example.com", "secret123", "token-1"))
                .thenReturn(Result.failure(HumanCheckRejectedException()))
            val viewModel = viewModel()
            viewModel.fill()
            viewModel.onHumanCheckToken("token-1")
            val resetBefore = viewModel.state.value.humanCheck.resetKey

            viewModel.events.test {
                viewModel.onSubmit()
                advanceUntilIdle()
                val event = awaitItem() as LoginEvent.ShowLoginError
                assertTrue(event.message.orEmpty().startsWith("The security check"))
            }
            assertNull(viewModel.state.value.humanCheck.token)
            assertEquals(resetBefore + 1, viewModel.state.value.humanCheck.resetKey)
            assertFalse(viewModel.state.value.canSubmit)
        }

    @Test
    fun `a wrong password also spends the token`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        whenever(signInUseCase.invoke("user@example.com", "secret123", "token-1"))
            .thenReturn(Result.failure(IllegalStateException("Invalid login credentials")))
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.onHumanCheckToken("token-1")

        viewModel.events.test {
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(LoginEvent.ShowLoginError("Invalid login credentials"), awaitItem())
        }
        assertNull(viewModel.state.value.humanCheck.token)
    }

    @Test
    fun `an expired token blocks the button until the widget brings a new one`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            viewModel.fill()
            viewModel.onHumanCheckToken("token-1")
            viewModel.onHumanCheckExpired()

            assertFalse(viewModel.state.value.canSubmit)
            viewModel.onHumanCheckToken("token-2")
            assertTrue(viewModel.state.value.canSubmit)
        }

    @Test
    fun `a check that could not load is shown and can be retried`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            viewModel.onHumanCheckError()
            assertTrue(viewModel.state.value.humanCheck.failed)

            viewModel.onHumanCheckRetry()
            assertFalse(viewModel.state.value.humanCheck.failed)
            assertEquals(1, viewModel.state.value.humanCheck.resetKey)
        }
}
