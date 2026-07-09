package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSyncStatusTest {

    @Test
    fun `fromValue resolves each known stored string`() {
        assertEquals(SessionSyncStatus.PENDING, SessionSyncStatus.fromValue("pending"))
        assertEquals(SessionSyncStatus.SYNCED, SessionSyncStatus.fromValue("synced"))
        assertEquals(SessionSyncStatus.SYNC_FAILED, SessionSyncStatus.fromValue("sync_failed"))
    }

    @Test
    fun `fromValue defaults to PENDING for an unrecognized stored string`() {
        assertEquals(SessionSyncStatus.PENDING, SessionSyncStatus.fromValue("bogus"))
    }
}
