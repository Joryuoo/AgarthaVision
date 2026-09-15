package com.agarthavision.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Compose UI tests for [AppearanceCard].
 *
 * The key behaviour being tested is that the *whole card* is the tap target, not only the
 * sun/moon glyph at its edge. Previously only an [IconButton] was tappable; this suite
 * would have failed against that implementation.
 *
 * Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppearanceCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Tappability ──────────────────────────────────────────────────────────

    /**
     * Core regression: clicking the "Dark mode" label text — not the icon — must
     * invoke [onToggleTheme]. Before this fix only the IconButton area did so.
     *
     * The label lives inside a Row with mergeDescendants = true, so we need
     * useUnmergedTree = true to reach the raw Text node and prove that tapping
     * that specific pixel area dispatches the action.
     */
    @Test
    fun `clicking the dark mode label text invokes onToggleTheme`() {
        var callCount = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = false, onToggleTheme = { callCount++ })
            }
        }

        composeRule
            .onNodeWithText("Dark mode", useUnmergedTree = true)
            .performClick()

        assertEquals(1, callCount)
    }

    @Test
    fun `clicking the card when already in dark mode invokes onToggleTheme`() {
        var callCount = 0
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = true, onToggleTheme = { callCount++ })
            }
        }

        composeRule
            .onNodeWithText("Dark mode", useUnmergedTree = true)
            .performClick()

        assertEquals(1, callCount)
    }

    // ── Content descriptions ─────────────────────────────────────────────────

    @Test
    fun `when not in dark mode the merged node has contentDescription theme_toggle_to_dark`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = false, onToggleTheme = {})
            }
        }

        // "Switch to dark mode" is the string resource value of R.string.theme_toggle_to_dark
        composeRule
            .onNodeWithContentDescription("Switch to dark mode")
            .assertIsDisplayed()
    }

    @Test
    fun `when in dark mode the merged node has contentDescription theme_toggle_to_light`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = true, onToggleTheme = {})
            }
        }

        // "Switch to light mode" is the string resource value of R.string.theme_toggle_to_light
        composeRule
            .onNodeWithContentDescription("Switch to light mode")
            .assertIsDisplayed()
    }

    @Test
    fun `contentDescription flips when isDarkMode changes from false to true`() {
        var dark by mutableStateOf(false)
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = dark, onToggleTheme = {})
            }
        }
        composeRule.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed()

        // Drive the state change and wait for recomposition.
        composeRule.runOnIdle { dark = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
    }

    // ── Role ─────────────────────────────────────────────────────────────────

    @Test
    fun `the merged node carries Role Switch when isDarkMode is false`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = false, onToggleTheme = {})
            }
        }

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsDisplayed()
    }

    @Test
    fun `the merged node carries Role Switch when isDarkMode is true`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = true, onToggleTheme = {})
            }
        }

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsDisplayed()
    }

    // ── Icon has no duplicate content description ────────────────────────────

    /**
     * The icon must have contentDescription = null so screen readers don't announce
     * both the merged row label and a second description for the glyph.
     * If a node with the toggle description exists, there must be exactly one of them.
     */
    @Test
    fun `only one node carries the toggle content description when isDarkMode is false`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(isDarkMode = false, onToggleTheme = {})
            }
        }

        // onAllNodesWithContentDescription would throw if 0 nodes; onNodeWithContentDescription
        // already asserts exactly one. Confirm the label-text node is not also a separate node.
        composeRule
            .onNodeWithContentDescription("Switch to dark mode")
            .assertIsDisplayed()

        // There should be no node for "Switch to light mode" (wrong direction label absent)
        composeRule
            .onNodeWithContentDescription("Switch to light mode")
            .assertDoesNotExist()
    }
}
