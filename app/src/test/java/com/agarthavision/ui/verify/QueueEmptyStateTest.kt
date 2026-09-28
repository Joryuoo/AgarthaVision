package com.agarthavision.ui.verify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [QueueEmptyState].
 *
 * Each variant is driven through the state/action seam — no Hilt, no ViewModel.
 * Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 *
 * Two variants, since the queue holds unverified rows only. The NONE_VERIFIED case went with the
 * Verified bucket: there is no longer a list of verified samples for it to be empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class QueueEmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------- NEVER_HAD ----------

    @Test
    fun `NEVER_HAD shows its title`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.NEVER_HAD, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText("Nothing captured yet").assertIsDisplayed()
    }

    @Test
    fun `NEVER_HAD shows its body`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.NEVER_HAD, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText(
            "Tap the shutter to record a field. Frames wait here until you verify them."
        ).assertIsDisplayed()
    }

    @Test
    fun `NEVER_HAD does not show View session records button`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.NEVER_HAD, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText("View session records").assertDoesNotExist()
    }

    // ---------- ALL_DONE ----------

    @Test
    fun `ALL_DONE shows its title`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.ALL_DONE, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText("All frames verified").assertIsDisplayed()
    }

    @Test
    fun `ALL_DONE shows its body`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.ALL_DONE, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText("Every frame captured in this session has been checked.").assertIsDisplayed()
    }

    @Test
    fun `ALL_DONE shows View session records button`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.ALL_DONE, onViewRecords = {})
            }
        }

        composeRule.onNodeWithText("View session records").assertIsDisplayed()
    }

    @Test
    fun `ALL_DONE clicking View session records invokes onViewRecords`() {
        var called = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.ALL_DONE, onViewRecords = { called++ })
            }
        }

        composeRule.onNodeWithText("View session records").performClick()

        assertEquals(1, called)
    }

    @Test
    fun `clicking View session records multiple times reports each click`() {
        var called = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                QueueEmptyState(variant = QueueEmptyVariant.ALL_DONE, onViewRecords = { called++ })
            }
        }

        composeRule.onNodeWithText("View session records").performClick()
        composeRule.onNodeWithText("View session records").performClick()

        assertEquals(2, called)
    }
}
