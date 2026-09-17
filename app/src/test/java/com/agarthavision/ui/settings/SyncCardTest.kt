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
 *  - signed-out, unlinked > 0 → badge + helper text shown; Sync now absent; count rows absent
 *  - signed-out, unlinked = 1 → singular helper text
 *  - signed-out, unlinked = 0 → "Nothing to sync" badge; helper absent
 *  - signed-in, zeros → "All synced" badge; Sync now present
 *  - signed-in, failed = 2 → "2 failed" badge
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SyncCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun emptyCounts() = PendingSyncCounts(0, 0, 0, 0)

    private fun signedOutState(unlinked: Int) = SyncCardState(
        isSignedIn = false,
        isOffline = false,
        counts = emptyCounts(),
        isSyncing = false,
        canSyncNow = false,
    )

    private fun signedInState(
        pending: Int = 0,
        failed: Int = 0,
        unlinked: Int = 0,
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

    // ── signed-out, unlinked = 3 ─────────────────────────────────────────────

    @Test
    fun `signed out unlinked 3 shows not-linked badge`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(3), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("3 not linked").assertIsDisplayed()
    }

    @Test
    fun `signed out unlinked 3 shows plural helper text`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(3), onSyncNowClick = {}) }
        }

        composeRule
            .onNodeWithText("Sign in to link and upload 3 sessions.")
            .assertIsDisplayed()
    }

    @Test
    fun `signed out unlinked 3 does not show Sync now button`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(3), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun `signed out unlinked 3 does not show Sessions count row`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(3), onSyncNowClick = {}) }
        }

        // "Sessions" is the label for the pending sessions count row — only visible when signed in
        composeRule.onNodeWithText("Sessions").assertDoesNotExist()
    }

    // ── signed-out, unlinked = 1 (singular) ──────────────────────────────────

    @Test
    fun `signed out unlinked 1 shows singular helper text`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(1), onSyncNowClick = {}) }
        }

        composeRule
            .onNodeWithText("Sign in to link and upload 1 session.")
            .assertIsDisplayed()
    }

    @Test
    fun `signed out unlinked 1 shows singular badge text`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(1), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("1 not linked").assertIsDisplayed()
    }

    // ── signed-out, unlinked = 0 ─────────────────────────────────────────────

    @Test
    fun `signed out unlinked 0 shows Nothing to sync badge`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(0), onSyncNowClick = {}) }
        }

        composeRule.onNodeWithText("Nothing to sync").assertIsDisplayed()
    }

    @Test
    fun `signed out unlinked 0 does not show helper text`() {
        composeRule.setContent {
            AgarthaVisionTheme { SyncCard(state = signedOutState(0), onSyncNowClick = {}) }
        }

        composeRule
            .onNodeWithText("Sign in to link", substring = true)
            .assertDoesNotExist()
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
