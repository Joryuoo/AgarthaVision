package com.agarthavision.ui.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class KpiPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTiles = listOf(
        KpiTileUi(
            kind = KpiKind.SESSIONS,
            label = "Sessions",
            value = "60",
            subtitle = "10 patients",
        ),
        KpiTileUi(
            kind = KpiKind.POSITIVE_RATE,
            label = "Positive rate",
            value = "20%",
            subtitle = "12 of 60 smears",
        ),
        KpiTileUi(
            kind = KpiKind.TO_REVIEW,
            label = "To review",
            value = "4",
            subtitle = "19 verified today",
        ),
        KpiTileUi(
            kind = KpiKind.AI_AGREEMENT,
            label = "AI agreement",
            value = "92%",
            subtitle = "8 of 100 corrected by you",
        ),
    )

    @Test
    fun `clicking dot 2 moves to page 1 and hides hint`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                KpiPager(
                    tiles = sampleTiles,
                    isLoading = false,
                    period = HomePeriod.TODAY,
                )
            }
        }

        // On page 0 initially
        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithTag("pagerCoverageHint").assertIsDisplayed()
        composeRule.onNodeWithText("Swipe for My coverage →").assertIsDisplayed()

        // Click dot 2
        composeRule.onNodeWithContentDescription("Page 2 of 2, My coverage").performClick()
        composeRule.waitForIdle()

        // Page 1 is displayed
        composeRule.onNodeWithTag("coveragePlaceholderCard").assertIsDisplayed()
        composeRule.onNodeWithText("My coverage").assertIsDisplayed()

        // Hint is hidden on page 1
        composeRule.onNodeWithTag("pagerCoverageHint").assertDoesNotExist()
    }

    @Test
    fun `custom actions move between pages`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                KpiPager(
                    tiles = sampleTiles,
                    isLoading = false,
                    period = HomePeriod.TODAY,
                )
            }
        }

        val node = composeRule.onNodeWithTag("kpiPager").fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(2, actions.size)

        // Show My coverage
        val showCoverage = actions.first { it.label == "Show My coverage" }
        val coverageResult = showCoverage.action()
        assertTrue(coverageResult)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("coveragePlaceholderCard").assertIsDisplayed()
        composeRule.onNodeWithTag("pagerCoverageHint").assertDoesNotExist()

        // Show activity tiles
        val showActivity = actions.first { it.label == "Show activity tiles" }
        val activityResult = showActivity.action()
        assertTrue(activityResult)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithTag("pagerCoverageHint").assertIsDisplayed()
    }
}
