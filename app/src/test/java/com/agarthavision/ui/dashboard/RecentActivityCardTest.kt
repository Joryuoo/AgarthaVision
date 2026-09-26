package com.agarthavision.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RecentActivityCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val nowMillis = 10_000_000L

    private val allTypesItems = listOf(
        ActivityItem.FramesVerified(
            sessionId = "s-1",
            sessionLabel = "Smear 1",
            count = 3,
            occurredAt = nowMillis - 1_000L,
        ),
        ActivityItem.FramesCaptured(
            sessionId = "s-1",
            sessionLabel = "Smear 1",
            count = 4,
            occurredAt = nowMillis - 2_000L,
        ),
        ActivityItem.PatientAdded(
            patientId = "p-1",
            maskedName = "R.*, J*.",
            occurredAt = nowMillis - 3_000L,
        ),
        ActivityItem.SessionStarted(
            sessionId = "s-2",
            sessionLabel = "Smear 2",
            occurredAt = nowMillis - 4_000L,
        ),
        ActivityItem.SyncFinished(
            itemCount = 5,
            occurredAt = nowMillis - 5_000L,
        ),
    )

    @Test
    fun `renders all 5 item-type rows with correct text`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                RecentActivityCard(
                    items = allTypesItems,
                    nowMillis = nowMillis,
                    onItemClick = {},
                    onSeeAllClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Verified 3 frames").assertIsDisplayed()
        composeRule.onNodeWithText("Captured 4 frames").assertIsDisplayed()
        composeRule.onNodeWithText("Added patient R.*, J*.").assertIsDisplayed()
        composeRule.onNodeWithText("Started a session").assertIsDisplayed()
        composeRule.onNodeWithText("Synced 5 items").assertIsDisplayed()
    }

    @Test
    fun `more than 5 items shows only the first 5 rows`() {
        val sixItems = (0 until 6).map { i ->
            ActivityItem.SessionStarted(
                sessionId = "s-$i",
                sessionLabel = "Smear $i",
                occurredAt = nowMillis - i * 1_000L,
            )
        }
        composeRule.setContent {
            AgarthaVisionTheme {
                RecentActivityCard(
                    items = sixItems,
                    nowMillis = nowMillis,
                    onItemClick = {},
                    onSeeAllClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("activityRow_4").assertIsDisplayed()
        composeRule.onNodeWithTag("activityRow_5").assertDoesNotExist()
    }

    @Test
    fun `empty list shows EmptyState`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                RecentActivityCard(
                    items = emptyList(),
                    nowMillis = nowMillis,
                    onItemClick = {},
                    onSeeAllClick = {},
                )
            }
        }

        composeRule.onNodeWithText("No activity yet").assertIsDisplayed()
    }

    @Test
    fun `row click invokes the callback with the right item`() {
        var clicked: ActivityItem? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                RecentActivityCard(
                    items = allTypesItems,
                    nowMillis = nowMillis,
                    onItemClick = { clicked = it },
                    onSeeAllClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("activityRow_0").performClick()

        assert(clicked == allTypesItems[0])
    }

    @Test
    fun `See all click invokes its callback`() {
        var seeAllClicked = false
        composeRule.setContent {
            AgarthaVisionTheme {
                RecentActivityCard(
                    items = allTypesItems,
                    nowMillis = nowMillis,
                    onItemClick = {},
                    onSeeAllClick = { seeAllClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("See all").performClick()

        assert(seeAllClicked)
    }
}
