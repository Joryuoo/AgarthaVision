package com.agarthavision.ui.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    // Page 1 (index 1) now hosts the Hilt-backed MyCoverageCard (see MyCoverageCardTest for its
    // own content coverage), so these tests can no longer navigate all the way to page 1 without
    // a Hilt test harness this Robolectric setup doesn't have — doing so throws
    // "does not implement GeneratedComponent" from hiltViewModel(). They stay scoped to what's
    // observable on page 0: initial content and that the navigation affordances exist.

    @Test
    fun `page 0 shows tiles and the swipe-to-coverage hint`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                KpiPager(
                    tiles = sampleTiles,
                    isLoading = false,
                    period = HomePeriod.TODAY,
                )
            }
        }

        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithTag("pagerCoverageHint").assertIsDisplayed()
        composeRule.onNodeWithText("Swipe for My coverage →").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page 2 of 2, My coverage").assertExists()
    }

    @Test
    fun `custom accessibility actions to move between pages are wired`() {
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
        assertTrue(actions.any { it.label == "Show My coverage" })
        assertTrue(actions.any { it.label == "Show activity tiles" })
    }
}
