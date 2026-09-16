package com.agarthavision.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ScreenHeader].
 *
 * Covers: title + purpose always rendered, optional status present/absent,
 * and a couple of boundary strings. Runs on the JVM under Robolectric inside
 * `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ScreenHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Required fields ───────────────────────────────────────────────────────

    @Test
    fun `title is rendered`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(title = "Sessions", purpose = "Your active and past testing sessions")
            }
        }

        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
    }

    @Test
    fun `purpose is rendered`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(title = "Sessions", purpose = "Your active and past testing sessions")
            }
        }

        composeRule.onNodeWithText("Your active and past testing sessions").assertIsDisplayed()
    }

    @Test
    fun `both title and purpose are visible together`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(
                    title = "Records",
                    purpose = "Search and review all logged results across sessions",
                )
            }
        }

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithText("Search and review all logged results across sessions").assertIsDisplayed()
    }

    // ── Optional status ───────────────────────────────────────────────────────

    @Test
    fun `status text is rendered when provided`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(
                    title = "Sessions",
                    purpose = "Your active and past testing sessions",
                    status = "3 sessions · 1 active",
                )
            }
        }

        composeRule.onNodeWithText("3 sessions · 1 active").assertIsDisplayed()
    }

    @Test
    fun `status text is absent when null`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(
                    title = "Sessions",
                    purpose = "Your active and past testing sessions",
                    status = null,
                )
            }
        }

        // Only title and purpose should appear; the status line must not exist at all.
        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Your active and past testing sessions").assertIsDisplayed()
    }

    @Test
    fun `status text is absent when the parameter is omitted (default null)`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(
                    title = "Settings",
                    purpose = "Manage your account, sync, and app preferences",
                )
            }
        }

        // Status line should not be in the tree at all.
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Manage your account, sync, and app preferences").assertIsDisplayed()
    }

    // ── Boundary strings ──────────────────────────────────────────────────────

    @Test
    fun `empty title string is handled without crash`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(title = "", purpose = "Some purpose")
            }
        }

        composeRule.onNodeWithText("Some purpose").assertIsDisplayed()
    }

    @Test
    fun `empty purpose string is handled without crash`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(title = "Title", purpose = "")
            }
        }

        composeRule.onNodeWithText("Title").assertIsDisplayed()
    }

    @Test
    fun `all three fields render when status is a non-empty string`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ScreenHeader(
                    title = "Records",
                    purpose = "Search and review all logged results across sessions",
                    status = "Filtered date range",
                )
            }
        }

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithText("Search and review all logged results across sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Filtered date range").assertIsDisplayed()
    }
}
