package com.agarthavision.ui.records

import com.agarthavision.domain.model.Session
import com.agarthavision.domain.usecase.records.SessionSamples
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the [SessionDetailState.canOpenVerifyQueue] computed property.
 *
 * No Hilt, no coroutines. State is constructed directly.
 */
class SessionDetailStateTest {

    // ---------- canOpenVerifyQueue == true ----------

    @Test
    fun `canOpenVerifyQueue is true when session is active and pendingFlagged is positive`() {
        val state = stateWith(endedAt = null, pendingFlagged = 1)
        assertTrue(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is true for multiple pending flagged frames`() {
        val state = stateWith(endedAt = null, pendingFlagged = 10)
        assertTrue(state.canOpenVerifyQueue)
    }

    // ---------- canOpenVerifyQueue == false ----------

    @Test
    fun `canOpenVerifyQueue is false when session has ended`() {
        val state = stateWith(endedAt = 2_000L, pendingFlagged = 5)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when pendingFlagged is zero even if session is active`() {
        val state = stateWith(endedAt = null, pendingFlagged = 0)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when session is ended and no pending frames`() {
        val state = stateWith(endedAt = 1_000L, pendingFlagged = 0)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when session is null`() {
        val state = SessionDetailState(session = null, pendingFlagged = 3)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when session is null and pendingFlagged is zero`() {
        val state = SessionDetailState(session = null, pendingFlagged = 0)
        assertFalse(state.canOpenVerifyQueue)
    }

    // ---------- helpers ----------

    private fun stateWith(endedAt: Long?, pendingFlagged: Int): SessionDetailState {
        val session = Session(
            id = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = 1_000L,
            endedAt = endedAt,
            notes = null,
            label = null,
        )
        return SessionDetailState(
            session = SessionSamples(session = session, samples = emptyList()),
            pendingFlagged = pendingFlagged,
        )
    }
}
