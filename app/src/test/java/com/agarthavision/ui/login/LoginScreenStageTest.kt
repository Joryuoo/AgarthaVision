package com.agarthavision.ui.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.components.rememberAgarthaToastState
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LoginScreenStageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: LoginUiState) {
        composeRule.setContent {
            AgarthaVisionTheme {
                LoginScreenContent(
                    state = state,
                    actions = LoginActions({}, {}, {}),
                    toastState = rememberAgarthaToastState(),
                )
            }
        }
    }

    @Test
    fun signingInStage_showsItsText() {
        show(LoginUiState(isSubmitting = true, stage = LoginStage.SIGNING_IN))

        composeRule.onNodeWithText("Signing in…").assertIsDisplayed()
    }

    @Test
    fun downloadingStage_showsItsText() {
        show(LoginUiState(isSubmitting = true, stage = LoginStage.DOWNLOADING_PATIENTS))

        composeRule.onNodeWithText("Downloading your patients…").assertIsDisplayed()
    }

    @Test
    fun noStage_showsNoProgressText() {
        show(LoginUiState())

        composeRule.onNodeWithText("Signing in…").assertDoesNotExist()
        composeRule.onNodeWithText("Downloading your patients…").assertDoesNotExist()
    }
}
