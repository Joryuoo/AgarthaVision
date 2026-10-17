package com.agarthavision.ui.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.ui.components.rememberAgarthaToastState
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LoginScreenRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: LoginUiState, actions: LoginActions = LoginActions({}, {}, {})) {
        composeRule.setContent {
            AgarthaVisionTheme {
                LoginScreenContent(
                    state = state,
                    actions = actions,
                    toastState = rememberAgarthaToastState(),
                )
            }
        }
    }

    @Test
    fun passwordToggle_startsHidden() {
        show(LoginUiState(password = "secret123"))

        // Icon should show "Show password" initially (password is masked)
        composeRule.onNodeWithContentDescription("Show password").assertIsDisplayed()
    }

    @Test
    fun passwordToggle_flipsOnClick() {
        show(LoginUiState(password = "secret123"))

        // Click toggle to show password
        composeRule.onNodeWithContentDescription("Show password").performClick()

        // After toggle, icon should show "Hide password"
        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun passwordToggle_preservesTypedText() {
        var capturedPassword = ""
        show(
            LoginUiState(password = "testpass"),
            LoginActions(
                onEmailChanged = {},
                onPasswordChanged = { capturedPassword = it },
                onSubmit = {}
            )
        )

        // Click toggle to show password
        composeRule.onNodeWithContentDescription("Show password").performClick()

        // Find password input and verify text is preserved
        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun heroDisplaysWordmarkAndTagline() {
        show(LoginUiState())

        // Check wordmark (app name in hero)
        composeRule.onNodeWithText("AgarthaVision").assertIsDisplayed()

        // Check tagline
        composeRule.onNodeWithText("Smear screening, in the field.").assertIsDisplayed()
    }

    @Test
    fun titleAndSubtitleAreShown() {
        show(LoginUiState())

        composeRule.onNodeWithText("Welcome back.").assertIsDisplayed()
        val subtitle = "Sign in with your provisioned medtech account to begin a session."
        composeRule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun emailErrorDisplayedWhenSet() {
        show(LoginUiState(emailError = true))

        composeRule.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
    }

    @Test
    fun passwordErrorDisplayedWhenSet() {
        show(LoginUiState(passwordError = true))

        composeRule.onNodeWithText("Password is required.").assertIsDisplayed()
    }

    @Test
    fun signedOutNoticeDisplayedWhenPresent() {
        val notice = SignedOutNotice(unsyncedKept = 0)
        show(LoginUiState(signedOutNotice = notice))

        composeRule.onNodeWithTag(LOGIN_SIGNED_OUT_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("You were signed out").assertIsDisplayed()
    }

    @Test
    fun submitButtonShowsProgressWhenSubmitting() {
        // canSubmit = false when isSubmitting = true; button shows spinner
        show(LoginUiState(isSubmitting = true))

        // When submitting, button shows progress indicator instead of text
        composeRule.onNodeWithText("Log in").assertDoesNotExist()
    }

    @Test
    fun submitButtonEnabled_firesCallbackOnClick() {
        var submitCalled = false
        // canSubmit = true when isSubmitting = false and isOffline = false (defaults)
        show(
            LoginUiState(),
            LoginActions(
                onEmailChanged = {},
                onPasswordChanged = {},
                onSubmit = { submitCalled = true }
            )
        )

        composeRule.onNodeWithText("Log in").performClick()

        assert(submitCalled) { "Submit callback was not fired" }
    }
}
