package com.agarthavision.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose tests for [SyncCard].
 *
 * Covers:
 *  - signed-out → "Nothing to sync" badge; Sync now absent; count rows absent
 *  - signed-in, zeros → "All synced" badge; Sync now present
 *  - signed-in, failed = 2 → "2 failed" badge
 *
 * The unlinked-session cases are gone with the state they tested. Mandatory first-run login
 * (86d4be3ke) means a session always has an owner, so there is no NOT_LINKED badge and no
 * "Sign in to link and upload N sessions" copy left to assert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SyncCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun emptyCounts() = PendingSyncCounts(0, 0, 0, 0)

    private fun signedOutState() = SyncCardState(
        isSignedIn = false,
        isOffline = false,
        counts = emptyCounts(),
        isSyncing = false,
        canSyncNow = false,
    )

    private fun signedInState(
        pending: Int = 0,
        failed: Int = 0,
    ) = SyncCardState(
        isSignedIn = true,
        isOffline = false,
        counts = PendingSyncCounts(
            pendingSessions = pending,
            pendingSamples = 0,
            pendingReports = 0,
            failed = failed,
        ),
        isSyncing = false,
        canSyncNow = true,
    )

    // ── signed-out ───────────────────────────────────────────────────

    @Test
    fun `signed out does not show Sync now button`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun `signed out does not show the Sessions count row`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(), onSyncNowClick = {}) }
        }

        // "Sessions" labels the pending-sessions count row — only visible when signed in.
        composeRule.onNodeWithText("Sessions").assertDoesNotExist()
    }

    @Test
    fun `signed out shows the Nothing to sync badge`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("Nothing to sync").assertIsDisplayed()
    }

    // ── signed-in, zeros ─────────────────────────────────────────────────────

    @Test
    fun `signed in zeros shows All synced badge`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedInState(), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("All synced").assertIsDisplayed()
    }

    @Test
    fun `signed in zeros shows Sync now button`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedInState(), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("Sync now").assertIsDisplayed()
    }

    // ── signed-in, failed = 2 ────────────────────────────────────────────────

    @Test
    fun `signed in failed 2 shows 2 failed badge`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedInState(failed = 2), onSyncNowClick = {}) }
        }

        // "2 failed" appears in both the badge and the SyncCounts detail row when signed in.
        // Verify at least one node is displayed (the badge); onFirst targets the topmost match.
        composeRule.onAllNodesWithText("2 failed")[0].assertIsDisplayed()
    }
}
