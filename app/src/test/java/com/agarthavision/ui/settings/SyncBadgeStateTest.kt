package com.agarthavision.ui.settings

import com.agarthavision.domain.model.PendingSyncCounts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JUnit tests for [syncBadgeState].
 *
 * No Android framework dependencies — this function is a plain Kotlin expression so
 * it can run on the JVM without Robolectric.
 */
class SyncBadgeStateTest {

    private fun counts(
        pending: Int = 0,
        failed: Int = 0,
    ) = PendingSyncCounts(
        pendingSessions = pending,
        pendingSamples = 0,
        pendingReports = 0,
        failed = failed,
    )

    // ── Signed-in: failed wins over everything ───────────────────────────────

    @Test
    fun `signed in with failed gt 0 returns FAILED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(failed = 1),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with failed gt 0 and pending gt 0 returns FAILED not PENDING`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 5, failed = 2),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with failed gt 0 and unlinked gt 0 still returns FAILED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(failed = 3),
            unlinkedSessions = 5,
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    // ── Signed-in: pending ───────────────────────────────────────────────────

    @Test
    fun `signed in with pending gt 0 and failed 0 returns PENDING`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 3),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.PENDING, result)
    }

    // ── Signed-in: all zero → ALL_SYNCED ─────────────────────────────────────

    @Test
    fun `signed in with zeros returns ALL_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.ALL_SYNCED, result)
    }

    @Test
    fun `signed in with zeros but unlinked 3 returns ALL_SYNCED not NOT_LINKED`() {
        // When signed in, unlinked is intentionally ignored (claim-on-login handles it).
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            unlinkedSessions = 3,
        )
        assertEquals(SyncBadge.ALL_SYNCED, result)
    }

    // ── Signed-out ───────────────────────────────────────────────────────────

    @Test
    fun `signed out with unlinked 3 returns NOT_LINKED`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(),
            unlinkedSessions = 3,
        )
        assertEquals(SyncBadge.NOT_LINKED, result)
    }

    @Test
    fun `signed out with unlinked 1 returns NOT_LINKED`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(),
            unlinkedSessions = 1,
        )
        assertEquals(SyncBadge.NOT_LINKED, result)
    }

    @Test
    fun `signed out with unlinked 0 returns NOTHING_TO_SYNC`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.NOTHING_TO_SYNC, result)
    }

    @Test
    fun `signed out with impossible pending gt 0 and unlinked gt 0 returns NOT_LINKED`() {
        // pending/failed are meaningless when signed out but the function must handle them gracefully.
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(pending = 5, failed = 2),
            unlinkedSessions = 3,
        )
        assertEquals(SyncBadge.NOT_LINKED, result)
    }

    @Test
    fun `signed out with impossible pending gt 0 and unlinked 0 returns NOTHING_TO_SYNC`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(pending = 5, failed = 2),
            unlinkedSessions = 0,
        )
        assertEquals(SyncBadge.NOTHING_TO_SYNC, result)
    }
}
