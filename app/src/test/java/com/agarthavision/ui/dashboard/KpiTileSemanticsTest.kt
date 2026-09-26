package com.agarthavision.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class KpiTileSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTiles = listOf(
        KpiTileUi(
            kind = KpiKind.SESSIONS,
            label = "Sessions",
            value = "60",
            subtitle = "10 patients",
            spokenDescription = "Sessions, 60, 10 patients. Opens sessions.",
        ),
        KpiTileUi(
            kind = KpiKind.POSITIVE_RATE,
            label = "Positive rate",
            value = "20%",
            subtitle = "12 of 60 smears",
            spokenDescription = "Positive rate, 20 percent, 12 of 60 smears. Opens examined smears.",
        ),
        KpiTileUi(
            kind = KpiKind.TO_REVIEW,
            label = "To review",
            value = "4",
            subtitle = "19 verified today",
            spokenDescription = "To review, 4 frames. Opens frames to review.",
        ),
        KpiTileUi(
            kind = KpiKind.AI_AGREEMENT,
            label = "AI agreement",
            value = "92%",
            subtitle = "8 of 100 corrected by you",
            spokenDescription = "AI agreement, 92 percent, 8 of 100 corrected by you. Opens AI agreement details.",
        ),
    )

    @Test
    fun `tiles render content and TalkBack descriptions`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                KpiGrid(
                    tiles = sampleTiles,
                    isLoading = false,
                    onTileClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("60").assertIsDisplayed()
        composeRule.onNodeWithText("10 patients").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Sessions, 60, 10 patients. Opens sessions.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Positive rate").assertIsDisplayed()
        composeRule.onNodeWithText("20%").assertIsDisplayed()
        composeRule.onNodeWithText("12 of 60 smears").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Positive rate, 20 percent, 12 of 60 smears. Opens examined smears.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("To review").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("19 verified today").assertIsDisplayed()

        composeRule.onNodeWithText("AI agreement").assertIsDisplayed()
        composeRule.onNodeWithText("92%").assertIsDisplayed()
        composeRule.onNodeWithText("8 of 100 corrected by you").assertIsDisplayed()
    }

    @Test
    fun `tapping each tile triggers expected KpiKind`() {
        var clickedKind: KpiKind? = null

        composeRule.setContent {
            AgarthaVisionTheme {
                KpiGrid(
                    tiles = sampleTiles,
                    isLoading = false,
                    onTileClick = { clickedKind = it },
                )
            }
        }

        composeRule.onNodeWithTag("kpiTile_Sessions").performClick()
        assertEquals(KpiKind.SESSIONS, clickedKind)

        composeRule.onNodeWithTag("kpiTile_Positive rate").performClick()
        assertEquals(KpiKind.POSITIVE_RATE, clickedKind)

        composeRule.onNodeWithTag("kpiTile_To review").performClick()
        assertEquals(KpiKind.TO_REVIEW, clickedKind)

        composeRule.onNodeWithTag("kpiTile_AI agreement").performClick()
        assertEquals(KpiKind.AI_AGREEMENT, clickedKind)
    }

    @Test
    fun `fallback tiles are rendered when tiles list is empty`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                KpiGrid(
                    tiles = emptyList(),
                    isLoading = false,
                    onTileClick = {},
                )
            }
        }

        composeRule.onNodeWithText("No smears examined yet").assertIsDisplayed()
        composeRule.onNodeWithText("No AI results reviewed yet").assertIsDisplayed()
    }
}
