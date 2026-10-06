package com.agarthavision.ui.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.domain.usecase.records.SessionSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the [SessionDetailState.canOpenVerifyQueue] computed property.
 *
 * No Hilt, no coroutines. State is constructed directly.
 *
 * The axis these cases turn on used to be `Session.endedAt`. It is now
 * [SessionDetailState.isActiveSession], fed from `SessionManager`. The behaviour did not go
 * away — the shortcut is still only offered on the session the app is working in — but the
 * old "session has ended" cases were describing a state no session ever reached, because
 * nothing writes `ended_at`. In production every one of them took the `true` branch.
 */
class SessionDetailStateTest {

    // ---------- canOpenVerifyQueue == true ----------

    @Test
    fun `canOpenVerifyQueue is true when session is active and pendingFlagged is positive`() {
        val state = stateWith(isActiveSession = true, pendingFlagged = 1)
        assertTrue(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is true for multiple pending flagged frames`() {
        val state = stateWith(isActiveSession = true, pendingFlagged = 10)
        assertTrue(state.canOpenVerifyQueue)
    }

    // ---------- canOpenVerifyQueue == false ----------

    @Test
    fun `canOpenVerifyQueue is false when this is not the active session`() {
        val state = stateWith(isActiveSession = false, pendingFlagged = 5)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when pendingFlagged is zero even if session is active`() {
        val state = stateWith(isActiveSession = true, pendingFlagged = 0)
        assertFalse(state.canOpenVerifyQueue)
    }

    @Test
    fun `canOpenVerifyQueue is false when inactive and no pending frames`() {
        val state = stateWith(isActiveSession = false, pendingFlagged = 0)
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

    // ---------- sessionResolved / unavailable defaults ----------

    @Test
    fun `default state has sessionResolved false`() {
        val state = SessionDetailState()
        assertFalse(state.sessionResolved)
    }

    @Test
    fun `default state has unavailable null`() {
        val state = SessionDetailState()
        assertNull(state.unavailable)
    }

    @Test
    fun `default state has selectedTab REPORT`() {
        val state = SessionDetailState()
        org.junit.Assert.assertEquals(SessionDetailTab.REPORT, state.selectedTab)
    }

    @Test
    fun `canOpenVerifyQueue is false when sessionResolved is false`() {
        // Simulate the loading skeleton: session not yet emitted, pendingFlagged unknown.
        val state = SessionDetailState(
            session = null,
            sessionResolved = false,
            pendingFlagged = 99,  // even a large pending count must not override unresolved
        )
        assertFalse(state.canOpenVerifyQueue)
    }

    // ---------- mapToUiModel gallery data tests ----------

    @Test
    fun `mapToUiModel sets isManual true for manual sample`() {
        val sample = sampleWith(isManual = true)
        val state = stateWithSamples(listOf(SampleRecordItem(sample = sample, detections = emptyList())))
        val uiModel = mapToUiModel(state)
        assertNotNull(uiModel)
        assertTrue(uiModel!!.verifiedSamples.first().isManual)
    }

    @Test
    fun `mapToUiModel sets eggCount to 2 and 2 boxes when 2 confirmed and 1 rejected`() {
        val sample = sampleWith(isManual = false)
        val detections = listOf(
            detection(verdict = DetectionVerdict.CONFIRMED, cx = 100f, cy = 100f, w = 50f, h = 50f),
            detection(verdict = DetectionVerdict.CONFIRMED, cx = 200f, cy = 200f, w = 60f, h = 60f),
            detection(verdict = DetectionVerdict.FALSE_POSITIVE, cx = 300f, cy = 300f, w = 40f, h = 40f),
        )
        val state = stateWithSamples(listOf(SampleRecordItem(sample = sample, detections = detections)))
        val uiModel = mapToUiModel(state)
        assertNotNull(uiModel)
        val sampleUi = uiModel!!.verifiedSamples.first()
        assertEquals(2, sampleUi.eggCount)
        assertEquals(2, sampleUi.boxes.size)
    }

    @Test
    fun `mapToUiModel sets isManual false for AI sample with zero detections`() {
        val sample = sampleWith(isManual = false)
        val state = stateWithSamples(listOf(SampleRecordItem(sample = sample, detections = emptyList())))
        val uiModel = mapToUiModel(state)
        assertNotNull(uiModel)
        assertFalse(uiModel!!.verifiedSamples.first().isManual)
    }

    // ---------- helpers ----------

    private fun sampleWith(isManual: Boolean): Sample = Sample(
        id = "sample-1",
        userId = "user-1",
        deviceId = "device-1",
        sessionId = "session-1",
        filePath = "/path/sample.jpg",
        isManual = isManual,
    )

    private fun detection(
        verdict: DetectionVerdict,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
    ): Detection = Detection(
        id = "det-1",
        sampleId = "sample-1",
        classLabel = "Ascaris lumbricoides",
        confidence = 0.9f,
        bboxX = cx,
        bboxY = cy,
        bboxW = w,
        bboxH = h,
        verdict = verdict,
        expertClass = null,
    )

    private fun stateWithSamples(samples: List<SampleRecordItem>): SessionDetailState {
        val session = Session(
            id = "session-1",
            userId = "user-1",
            patientId = "patient-1",
            deviceId = "device-1",
            startedAt = 1_000L,
            label = null,
        )
        return SessionDetailState(
            session = SessionSamples(session = session, samples = samples),
        )
    }

    private fun stateWith(isActiveSession: Boolean, pendingFlagged: Int): SessionDetailState {
        val session = Session(
            id = "session-1",
            userId = "user-1",
            patientId = "patient-1",
            deviceId = "device-1",
            startedAt = 1_000L,
            label = null,
        )
        return SessionDetailState(
            session = SessionSamples(session = session, samples = emptyList()),
            pendingFlagged = pendingFlagged,
            isActiveSession = isActiveSession,
        )
    }
}
