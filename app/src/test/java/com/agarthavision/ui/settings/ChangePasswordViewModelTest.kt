package com.agarthavision.ui.settings

import com.agarthavision.domain.model.HumanCheckConfig
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.usecase.auth.ChangePasswordUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val online = MutableStateFlow(true)
    private val changePasswordUseCase: ChangePasswordUseCase = mock()
    private val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
        whenever(it.currentlyOnline()).thenAnswer { online.value }
        whenever(it.isOnline).thenReturn(online)
    }

    private fun viewModel(humanCheck: HumanCheckConfig = HumanCheckConfig(siteKey = "", hostname = "")) =
        ChangePasswordViewModel(changePasswordUseCase, connectivityObserver, humanCheck)

    private fun ChangePasswordViewModel.fill(current: String, new: String, confirm: String) {
        onCurrentPasswordChanged(current)
        onNewPasswordChanged(new)
        onConfirmPasswordChanged(confirm)
    }

    @Test
    fun `an empty form marks every field and never calls the server`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onSubmit()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(PasswordFieldError.REQUIRED, state.currentError)
            assertEquals(PasswordFieldError.REQUIRED, state.newError)
            assertEquals(PasswordFieldError.REQUIRED, state.confirmError)
            verify(changePasswordUseCase, never()).invoke(any(), any(), anyOrNull())
        }

    @Test
    fun `a mismatched confirmation is caught on the phone`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "new-pass-1", confirm = "new-pass-2")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(PasswordFieldError.MISMATCH, viewModel.state.value.confirmError)
            verify(changePasswordUseCase, never()).invoke(any(), any(), anyOrNull())
        }

    @Test
    fun `a wrong current password lands on the current-password field`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("bad-pass", "new-pass", null))
                .thenReturn(PasswordChangeResult.WrongCurrentPassword)
            val viewModel = viewModel()
            viewModel.fill(current = "bad-pass", new = "new-pass", confirm = "new-pass")

            viewModel.onSubmit()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(PasswordFieldError.WRONG_CURRENT, state.currentError)
            assertFalse(state.isChanged)
            assertFalse(state.isSubmitting)
        }

    @Test
    fun `a weak password shows the provider's reason under the new password`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("old-pass", "short", null))
                .thenReturn(PasswordChangeResult.WeakPassword("At least 8 characters."))
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "short", confirm = "short")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(PasswordFieldError.WEAK, viewModel.state.value.newError)
            assertEquals("At least 8 characters.", viewModel.state.value.weakPasswordDetail)
        }

    @Test
    fun `success shows the done panel and forgets the passwords`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("old-pass", "new-pass", null))
                .thenReturn(PasswordChangeResult.Changed)
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "new-pass", confirm = "new-pass")

            viewModel.onSubmit()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isChanged)
            assertEquals("", state.currentPassword)
            assertEquals("", state.newPassword)
            assertEquals("", state.confirmPassword)
        }

    @Test
    fun `offline the form cannot be submitted`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            online.value = false
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.fill(current = "old-pass", new = "new-pass", confirm = "new-pass")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isOffline)
            assertFalse(viewModel.state.value.canSubmit)
            verify(changePasswordUseCase, never()).invoke(any(), any(), anyOrNull())
        }

    @Test
    fun `a new password of only spaces is refused, since the login form would refuse it too`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "      ", confirm = "      ")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(PasswordFieldError.REQUIRED, viewModel.state.value.newError)
            verify(changePasswordUseCase, never()).invoke(any(), any(), anyOrNull())
        }

    @Test
    fun `a connection lost after the new password was sent does not say nothing changed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("old-pass", "new-pass", null))
                .thenReturn(PasswordChangeResult.Unconfirmed)
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "new-pass", confirm = "new-pass")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(ChangePasswordFailure.UNCONFIRMED, viewModel.state.value.failure)
            assertFalse(viewModel.state.value.isChanged)
        }

    @Test
    fun `with the human check on, the button waits for it and its token goes with the check`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("old-pass", "new-pass", "token-1"))
                .thenReturn(PasswordChangeResult.HumanCheckRejected)
            val viewModel = viewModel(HumanCheckConfig(siteKey = "0x4AAAAAAA-test", hostname = "example.test"))
            viewModel.fill(current = "old-pass", new = "new-pass", confirm = "new-pass")

            assertFalse(viewModel.state.value.canSubmit)
            viewModel.onHumanCheckToken("token-1")
            assertTrue(viewModel.state.value.canSubmit)

            viewModel.onSubmit()
            advanceUntilIdle()

            verify(changePasswordUseCase).invoke("old-pass", "new-pass", "token-1")
            assertEquals(ChangePasswordFailure.HUMAN_CHECK_REJECTED, viewModel.state.value.failure)
            assertNull(viewModel.state.value.currentError)
            // The token is spent; the next try needs a fresh check.
            assertNull(viewModel.state.value.humanCheck.token)
            assertFalse(viewModel.state.value.canSubmit)
        }

    @Test
    fun `a dropped connection mid-request says nothing changed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(changePasswordUseCase.invoke("old-pass", "new-pass", null))
                .thenReturn(PasswordChangeResult.NoConnection)
            val viewModel = viewModel()
            viewModel.fill(current = "old-pass", new = "new-pass", confirm = "new-pass")

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(ChangePasswordFailure.NO_CONNECTION, viewModel.state.value.failure)
            assertNull(viewModel.state.value.currentError)
        }
}
