package com.agarthavision.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.usecase.home.NeedsAttention
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NeedsAttentionStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `hidden when empty`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(0, 0, 0),
                    onNavigateToTab = {},
                    onOpenSessionList = {},
                )
            }
        }

        composeRule.onNodeWithText("NEEDS ATTENTION").assertDoesNotExist()
    }

    @Test
    fun `renders non-zero chips only and supports TalkBack`() {
        var clickedTab: String? = null
        var clickedFilter: SessionListFilter? = null

        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(
                        unsyncedItems = 1,
                        framesToReview = 3,
                        emptySessions = 0,
                    ),
                    onNavigateToTab = { clickedTab = it },
                    onOpenSessionList = { clickedFilter = it },
                )
            }
        }

        composeRule.onNodeWithText("NEEDS ATTENTION").assertIsDisplayed()

        val unsyncedDesc = "1 unsynced item. Opens settings."
        composeRule.onNodeWithContentDescription(unsyncedDesc)
            .assertIsDisplayed()
            .performClick()
        assertEquals(Screen.Settings.route, clickedTab)

        val reviewDesc = "3 frames to review. Opens frames to review."
        composeRule.onNodeWithContentDescription(reviewDesc)
            .assertIsDisplayed()
            .performClick()
        assertEquals(SessionListFilter.TO_REVIEW, clickedFilter)

        val emptyDesc = "0 sessions with no frames. Opens sessions with no frames."
        composeRule.onNodeWithContentDescription(emptyDesc).assertDoesNotExist()
    }

    @Test
    fun `clicking empty sessions chip opens no frames filter`() {
        var clickedFilter: SessionListFilter? = null

        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(
                        unsyncedItems = 0,
                        framesToReview = 0,
                        emptySessions = 2,
                    ),
                    onNavigateToTab = {},
                    onOpenSessionList = { clickedFilter = it },
                )
            }
        }

        val emptyDesc = "2 sessions with no frames. Opens sessions with no frames."
        composeRule.onNodeWithContentDescription(emptyDesc)
            .assertIsDisplayed()
            .performClick()
        assertEquals(SessionListFilter.NO_FRAMES, clickedFilter)
    }
}
