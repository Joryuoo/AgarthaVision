package com.agarthavision.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the accessibility contract of [SkeletonBox]: [clearAndSetSemantics] must strip
 * every semantic property so screen readers skip the loading placeholder entirely.
 *
 * Runs on the JVM under Robolectric inside the `:app:testDebugUnitTest` gate.
 * Animation timing and pixel output are intentionally not covered here — neither is
 * cheaply testable in a unit environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SkeletonBoxTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Semantics clearing ──────────────────────────────────────────────────────────

    @Test
    fun `SkeletonBox strips a contentDescription passed via the modifier`() {
        // If clearAndSetSemantics {} were absent, this description would be reachable.
        composeRule.setContent {
            AgarthaVisionTheme {
                SkeletonBox(
                    modifier = Modifier
                        .size(120.dp)
                        .semantics { contentDescription = "loading placeholder" },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("loading placeholder")
            .assertDoesNotExist()
    }

    @Test
    fun `SkeletonBox strips a testTag passed via the modifier`() {
        // testTag is a semantics property; clearAndSetSemantics {} must erase it.
        composeRule.setContent {
            AgarthaVisionTheme {
                SkeletonBox(
                    modifier = Modifier
                        .size(120.dp)
                        .testTag("skeleton-loading"),
                )
            }
        }

        composeRule
            .onNodeWithTag("skeleton-loading")
            .assertDoesNotExist()
    }

    // ── Positive control: surrounding layout is not affected ────────────────────────

    @Test
    fun `a parent container wrapping SkeletonBox retains its own testTag`() {
        // clearAndSetSemantics {} must not escape the SkeletonBox boundary and eat
        // semantics of its ancestors.
        composeRule.setContent {
            AgarthaVisionTheme {
                Box(modifier = Modifier.testTag("skeleton-parent")) {
                    SkeletonBox(modifier = Modifier.size(120.dp))
                }
            }
        }

        composeRule
            .onNodeWithTag("skeleton-parent")
            .assertIsDisplayed()
    }
}
