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
import org.junit.Assert.assertNull
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

        composeRule.onNodeWithText("Needs attention").assertDoesNotExist()
    }

    @Test
    fun `renders summary with non-zero items and routes to review by priority`() {
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

        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("1 unsynced · 3 to review").assertIsDisplayed()

        // Clicking the unified card triggers the priority action (TO_REVIEW when framesToReview > 0)
        composeRule.onNodeWithContentDescription("Needs attention: 1 unsynced · 3 to review")
            .assertIsDisplayed()
            .performClick()

        assertEquals(SessionListFilter.TO_REVIEW, clickedFilter)
        assertNull(clickedTab)
    }

    @Test
    fun `renders full summary matching mockup text`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(
                        unsyncedItems = 3,
                        framesToReview = 2,
                        emptySessions = 1,
                    ),
                    onNavigateToTab = {},
                    onOpenSessionList = {},
                )
            }
        }

        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("3 unsynced · 2 to review · 1 session with no frames")
            .assertIsDisplayed()
    }

    @Test
    fun `clicking when only unsynced routes to settings`() {
        var clickedTab: String? = null
        var clickedFilter: SessionListFilter? = null

        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(
                        unsyncedItems = 2,
                        framesToReview = 0,
                        emptySessions = 0,
                    ),
                    onNavigateToTab = { clickedTab = it },
                    onOpenSessionList = { clickedFilter = it },
                )
            }
        }

        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("2 unsynced").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Needs attention: 2 unsynced")
            .assertIsDisplayed()
            .performClick()

        assertEquals(Screen.Settings.route, clickedTab)
        assertNull(clickedFilter)
    }

    @Test
    fun `clicking when only empty sessions routes to no frames filter`() {
        var clickedTab: String? = null
        var clickedFilter: SessionListFilter? = null

        composeRule.setContent {
            AgarthaVisionTheme {
                NeedsAttentionStrip(
                    needsAttention = NeedsAttention(
                        unsyncedItems = 0,
                        framesToReview = 0,
                        emptySessions = 2,
                    ),
                    onNavigateToTab = { clickedTab = it },
                    onOpenSessionList = { clickedFilter = it },
                )
            }
        }

        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("2 sessions with no frames").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Needs attention: 2 sessions with no frames")
            .assertIsDisplayed()
            .performClick()

        assertEquals(SessionListFilter.NO_FRAMES, clickedFilter)
        assertNull(clickedTab)
    }
}
