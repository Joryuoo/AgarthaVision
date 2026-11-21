package com.agarthavision.ui.sessions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.domain.model.AgreementBreakdown
import com.agarthavision.ui.components.BiometricLockHost
import com.agarthavision.ui.components.BiometricLockTestTags
import com.agarthavision.ui.dashboard.AiAgreementSheet
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SessionSheetsLockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `rename session sheet is hidden while locked and restored without dismissing on unlock`() {
        var isLocked by mutableStateOf(false)
        var dismissed = 0

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    RenameSessionSheet(
                        initialLabel = "Smear #3",
                        state = SessionsState(),
                        onClearError = {},
                        onDismiss = { dismissed++ },
                        onSubmit = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Smear #3").assertExists()

        // Lock app
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Smear #3").assertDoesNotExist()
        assertEquals(0, dismissed)

        // Unlock app
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Smear #3").assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun `ai agreement sheet is hidden while locked and restored on unlock`() {
        var isLocked by mutableStateOf(true)
        var dismissed = 0

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    AiAgreementSheet(
                        breakdown = AgreementBreakdown(
                            confirmed = 12,
                            wrongClass = 1,
                            boxIncorrect = 2,
                            falsePositive = 0,
                        ),
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }

        // Hidden while locked
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("AI Agreement Breakdown").assertDoesNotExist()
        assertEquals(0, dismissed)

        // Restored after unlock
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AI Agreement Breakdown").assertExists()
        assertEquals(0, dismissed)
    }
}

