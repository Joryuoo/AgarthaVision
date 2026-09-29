package com.agarthavision.ui.coverage

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IslandGroupChipsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a zero-count chip renders disabled`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = mapOf(IslandGroup.LUZON to 3, IslandGroup.VISAYAS to 0),
                    total = 3,
                    selected = null,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("Visayas").assertIsNotEnabled()
    }

    @Test
    fun `a non-zero chip is clickable and reports the right group`() {
        var selected: IslandGroup? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = mapOf(IslandGroup.LUZON to 3),
                    total = 3,
                    selected = null,
                    onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Luzon").assertIsEnabled().performClick()

        assert(selected == IslandGroup.LUZON)
    }

    @Test
    fun `All is only disabled when total is 0`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = emptyMap(),
                    total = 0,
                    selected = null,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("All").assertIsNotEnabled()
    }

    @Test
    fun `clicking a zero-count chip does not invoke onSelect`() {
        var invoked = false
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = mapOf(IslandGroup.LUZON to 3, IslandGroup.VISAYAS to 0),
                    total = 3,
                    selected = null,
                    onSelect = { invoked = true },
                )
            }
        }

        composeRule.onNodeWithText("Visayas").assertIsNotEnabled().performClick()

        assert(!invoked)
    }

    @Test
    fun `clicking a disabled All does not invoke onSelect`() {
        var invoked = false
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = emptyMap(),
                    total = 0,
                    selected = null,
                    onSelect = { invoked = true },
                )
            }
        }

        composeRule.onNodeWithText("All").assertIsNotEnabled().performClick()

        assert(!invoked)
    }

    @Test
    fun `All is enabled when total is nonzero even if every group is 0`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                IslandGroupChips(
                    counts = mapOf(IslandGroup.LUZON to 0, IslandGroup.VISAYAS to 0, IslandGroup.MINDANAO to 0),
                    total = 5,
                    selected = null,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("All").assertIsEnabled()
    }
}
