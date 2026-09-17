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
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with failed gt 0 and pending gt 0 returns FAILED not PENDING`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 5, failed = 2),
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with failed gt 0 and unlinked gt 0 still returns FAILED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(failed = 3),
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    // ── Signed-in: pending ───────────────────────────────────────────────────

    @Test
    fun `signed in with pending gt 0 and failed 0 returns PENDING`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 3),
        )
        assertEquals(SyncBadge.PENDING, result)
    }

    // ── Signed-in: all zero → ALL_SYNCED ─────────────────────────────────────

    @Test
    fun `signed in with zeros returns ALL_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
        )
        assertEquals(SyncBadge.ALL_SYNCED, result)
    }

    @Test
    fun `signed in with zeros but unlinked 3 returns ALL_SYNCED not NOT_LINKED`() {
        // When signed in, unlinked is intentionally ignored (claim-on-login handles it).
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
        )
        assertEquals(SyncBadge.ALL_SYNCED, result)
    }

    // ── Signed-out ───────────────────────────────────────────────────────────

    @Test
    fun `signed out with unlinked 0 returns NOTHING_TO_SYNC`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(),
        )
        assertEquals(SyncBadge.NOTHING_TO_SYNC, result)
    }

    @Test
    fun `signed out with impossible pending gt 0 and unlinked 0 returns NOTHING_TO_SYNC`() {
        val result = syncBadgeState(
            isSignedIn = false,
            counts = counts(pending = 5, failed = 2),
        )
        assertEquals(SyncBadge.NOTHING_TO_SYNC, result)
    }

    // ── New states: FETCHING and NOT_YET_SYNCED ──────────────────────────────

    @Test
    fun `signed in with isFetching true and zero counts returns FETCHING`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            initialFetchDone = true,
            isFetching = true,
        )
        assertEquals(SyncBadge.FETCHING, result)
    }

    @Test
    fun `signed in with failed gt 0 and isFetching returns FAILED not FETCHING (failed wins)`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(failed = 1),
            initialFetchDone = true,
            isFetching = true,
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with pending gt 0 and isFetching returns PENDING not FETCHING (pending wins)`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 2),
            initialFetchDone = true,
            isFetching = true,
        )
        assertEquals(SyncBadge.PENDING, result)
    }

    @Test
    fun `signed in with initialFetchDone false and zero counts returns NOT_YET_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            initialFetchDone = false,
            isFetching = false,
        )
        assertEquals(SyncBadge.NOT_YET_SYNCED, result)
    }

    @Test
    fun `signed in with initialFetchDone false but isFetching returns FETCHING not NOT_YET_SYNCED`() {
        // isFetching takes precedence over not-yet-synced (active fetch in progress)
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            initialFetchDone = false,
            isFetching = true,
        )
        assertEquals(SyncBadge.FETCHING, result)
    }

    @Test
    fun `signed in with initialFetchDone false but failed gt 0 returns FAILED not NOT_YET_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(failed = 1),
            initialFetchDone = false,
        )
        assertEquals(SyncBadge.FAILED, result)
    }

    @Test
    fun `signed in with initialFetchDone true and zero counts returns ALL_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(),
            initialFetchDone = true,
            isFetching = false,
        )
        assertEquals(SyncBadge.ALL_SYNCED, result)
    }

    @Test
    fun `signed in with initialFetchDone false and pending gt 0 returns PENDING not NOT_YET_SYNCED`() {
        val result = syncBadgeState(
            isSignedIn = true,
            counts = counts(pending = 3),
            initialFetchDone = false,
        )
        assertEquals(SyncBadge.PENDING, result)
    }

