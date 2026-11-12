package com.agarthavision.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BiometricLockOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `when locked overlay is displayed and underlying content is not displayed or clickable`() {
        var isLocked by mutableStateOf(true)
        var unlockClicked = 0
        var underlyingClicked = 0
        var observedLockState = false

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = { unlockClicked++ },
                ) {
                    observedLockState = LocalAppLocked.current
                    Button(
                        onClick = { underlyingClicked++ },
                        modifier = Modifier.testTag("underlying_button"),
                    ) {
                        Text("Secret Patient Action")
                    }
                }
            }
        }

        assertTrue(observedLockState)
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Unlock AgarthaVision").assertIsDisplayed()
        composeRule.onNodeWithTag("underlying_button").assertIsNotDisplayed()

        composeRule.onNodeWithTag(BiometricLockTestTags.UNLOCK_BUTTON).performClick()
        assertEquals(1, unlockClicked)
        assertEquals(0, underlyingClicked)

        // Unlock and verify content becomes visible and interactive
        isLocked = false
        composeRule.waitForIdle()

        assertFalse(observedLockState)
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertDoesNotExist()
        composeRule.onNodeWithTag("underlying_button").assertIsDisplayed().performClick()
        assertEquals(1, underlyingClicked)
    }

    @Test
    fun `system back is consumed while locked and does not invoke underlying back handler`() {
        var isLocked by mutableStateOf(true)
        var underlyingBackCount = 0

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    BackHandler {
                        underlyingBackCount++
                    }
                    Text("Underlying screen")
                }
            }
        }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(0, underlyingBackCount)

        isLocked = false
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(1, underlyingBackCount)
    }

    @Test
    fun `repeated lock and unlock cycles preserve remembered state in underlying content`() {
        var isLocked by mutableStateOf(false)

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    var editCount by remember { mutableIntStateOf(0) }
                    Button(
                        onClick = { editCount++ },
                        modifier = Modifier.testTag("counter_button"),
                    ) {
                        Text("Edits: $editCount")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("counter_button").performClick()
        composeRule.onNodeWithTag("counter_button").performClick()
        composeRule.onNodeWithText("Edits: 2").assertIsDisplayed()

        // First lock/unlock cycle
        isLocked = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Edits: 2").assertIsNotDisplayed()

        isLocked = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edits: 2").assertIsDisplayed()

        // Second lock/unlock cycle
        isLocked = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()

        isLocked = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edits: 2").assertIsDisplayed()
    }

    @Test
    fun `overlay renders in both light and dark themes`() {
        var darkTheme by mutableStateOf(false)

        composeRule.setContent {
            AgarthaVisionTheme(darkTheme = darkTheme) {
                BiometricLockHost(
                    isLocked = true,
                    onUnlockClick = {},
                ) {
                    Text("Hidden screen")
                }
            }
        }

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("AgarthaVision is Locked").assertIsDisplayed()

        darkTheme = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("AgarthaVision is Locked").assertIsDisplayed()
    }
}

