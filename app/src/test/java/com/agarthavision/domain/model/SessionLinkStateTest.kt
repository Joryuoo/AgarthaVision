package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM unit tests that pin the [Session.linkState] derivation rules from ADR-007.
 *
 * Rules under test:
 *  - userId == null && !claimExempt  → UNOWNED
 *  - userId == null &&  claimExempt  → NOT_LINKED
 *  - owned  + supabaseStatus == SYNCED      → SYNCED
 *  - owned  + supabaseStatus == PENDING     → PENDING
 *  - owned  + supabaseStatus == SYNC_FAILED → PENDING  (failed treated as still pending)
 */
class SessionLinkStateTest {

    // Minimal Session helper — only the fields that affect linkState vary per test.
    private fun session(
        userId: String? = "user-1",
        claimExempt: Boolean = false,
        supabaseStatus: SessionSyncStatus = SessionSyncStatus.SYNCED,
    ) = Session(
        id = "test-id",
        userId = userId,
        deviceId = "device-1",
        startedAt = 0L,
        endedAt = null,
        notes = null,
        label = null,
        supabaseStatus = supabaseStatus,
        claimExempt = claimExempt,
    )

    @Test
    fun `userId null and claimExempt false yields UNOWNED`() {
        val s = session(userId = null, claimExempt = false)
        assertEquals(SessionLinkState.UNOWNED, s.linkState)
    }

    @Test
    fun `userId null and claimExempt true yields NOT_LINKED`() {
        val s = session(userId = null, claimExempt = true)
        assertEquals(SessionLinkState.NOT_LINKED, s.linkState)
    }

    @Test
    fun `owned session with SYNCED status yields SYNCED`() {
        val s = session(userId = "owner", supabaseStatus = SessionSyncStatus.SYNCED)
        assertEquals(SessionLinkState.SYNCED, s.linkState)
    }

    @Test
    fun `owned session with PENDING status yields PENDING`() {
        val s = session(userId = "owner", supabaseStatus = SessionSyncStatus.PENDING)
        assertEquals(SessionLinkState.PENDING, s.linkState)
    }

    @Test
    fun `owned session with SYNC_FAILED status yields PENDING`() {
        val s = session(userId = "owner", supabaseStatus = SessionSyncStatus.SYNC_FAILED)
        assertEquals(SessionLinkState.PENDING, s.linkState)
    }

    // Edge: claimExempt is ignored when userId is non-null — ownership wins.
    @Test
    fun `owned session with claimExempt true still yields SYNCED`() {
        val s = session(userId = "owner", claimExempt = true, supabaseStatus = SessionSyncStatus.SYNCED)
        assertEquals(SessionLinkState.SYNCED, s.linkState)
    }
}
