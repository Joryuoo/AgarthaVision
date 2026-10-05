package com.agarthavision.ui.sessions

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun setCard(
        isColleagueSession: Boolean = false,
        onClick: () -> Unit = {},
        onRenameClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            AgarthaVisionTheme {
                SessionCard(
                    sessionData = sampleSessionWithStats(),
                    isActive = false,
                    isColleagueSession = isColleagueSession,
                    actions = SessionCardActions(onClick = onClick, onRenameClick = onRenameClick),
                )
            }
        }
    }

    @Test
    fun `long press on own card shows Rename and clicking it fires onRenameClick`() {
        var renameClicked = false
        setCard(onRenameClick = { renameClicked = true })

        composeRule.onNodeWithTag("sessionCard_s1").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").assertIsDisplayed().performClick()
        assertTrue("onRenameClick must fire when Rename is clicked", renameClicked)
    }

    @Test
    fun `plain tap fires onClick and does not open the menu`() {
        var clicked = false
        setCard(onClick = { clicked = true })

        composeRule.onNodeWithTag("sessionCard_s1").performClick()
        assertTrue(clicked)
        composeRule.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun `own card exposes Rename session accessibility action that fires rename`() {
        var renameClicked = false
        setCard(onRenameClick = { renameClicked = true })

        val node = composeRule.onNodeWithTag("sessionCard_s1")
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Rename session"), actions.map { it.label })
        actions.single().action()
        assertTrue(renameClicked)
    }

    @Test
    fun `own card has long click action labelled Show session actions`() {
        setCard()

        val config = composeRule.onNodeWithTag("sessionCard_s1").fetchSemanticsNode().config
        val longClick = config.getOrNull(SemanticsActions.OnLongClick)
        assertNotNull(longClick)
        assertEquals("Show session actions", longClick?.label)
    }

    @Test
    fun `colleague card has no long click and no custom actions`() {
        setCard(isColleagueSession = true)

        val config = composeRule.onNodeWithTag("sessionCard_s1").fetchSemanticsNode().config
        assertNull(config.getOrNull(SemanticsActions.OnLongClick))
        assertFalse(config.contains(SemanticsActions.CustomActions))
    }

    @Test
    fun `no kebab node exists`() {
        setCard()

        composeRule.onNodeWithTag("sessionKebab_s1").assertDoesNotExist()
    }
}
