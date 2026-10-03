package com.agarthavision.ui.sessions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSessionWithStats(
        id: String = "s1",
        label: String = "GARCIAM-S01",
    ) = SessionWithStats(
        session = Session(
            id = id,
            userId = "user-1",
            patientId = "pat-1",
            deviceId = "dev-1",
            startedAt = 1000L,
            label = label,
            supabaseStatus = SessionSyncStatus.SYNCED,
        ),
        totalSamples = 2,
        verifiedSamples = 2,
        unverifiedSamples = 0,
        totalEggs = 5,
    )

    @Test
    fun `own session card shows kebab menu and clicking it opens Rename option`() {
        var renameClicked = false
        composeRule.setContent {
            AgarthaVisionTheme {
                SessionCard(
                    sessionData = sampleSessionWithStats(),
                    isActive = false,
                    isColleagueSession = false,
                    actions = SessionCardActions(
                        onClick = {},
                        onRenameClick = { renameClicked = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("sessionKebab_s1").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Rename").assertIsDisplayed().performClick()
        assertTrue("onRenameClick must be triggered when Rename menu item is clicked", renameClicked)
    }

    @Test
    fun `colleague session card hides kebab menu`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                SessionCard(
                    sessionData = sampleSessionWithStats(),
                    isActive = false,
                    isColleagueSession = true,
                    actions = SessionCardActions(
                        onClick = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("sessionKebab_s1").assertDoesNotExist()
    }
}
