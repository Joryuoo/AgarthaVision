package com.agarthavision.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric Compose tests for [ChangePasswordContent] (14zcqntjph9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ChangePasswordContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noActions = ChangePasswordActions(
        onCurrentPasswordChanged = {},
        onNewPasswordChanged = {},
        onConfirmPasswordChanged = {},
        onSubmit = {},
        onBack = {},
    )

    private fun show(state: ChangePasswordUiState) {
        composeRule.setContent {
            AgarthaVisionTheme { ChangePasswordContent(state = state, actions = noActions) }
        }
    }

    @Test
    fun `offline the screen says it needs a connection and the button is disabled`() {
        show(ChangePasswordUiState(isOffline = true))

        composeRule.onNodeWithTag(CHANGE_PASSWORD_OFFLINE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CHANGE_PASSWORD_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a wrong current password is said in words`() {
        show(ChangePasswordUiState(currentError = PasswordFieldError.WRONG_CURRENT))

        composeRule.onNodeWithText("That isn't your current password.").assertIsDisplayed()
    }

    @Test
    fun `after a change the form gives way to the done panel`() {
        show(ChangePasswordUiState(isChanged = true))

        composeRule.onNodeWithTag(CHANGE_PASSWORD_DONE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Password changed").assertIsDisplayed()
    }
}
