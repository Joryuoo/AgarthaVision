package com.agarthavision.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.camera.CachedFrame
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.ElapsedClock
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.capture.CaptureFieldUseCase
import com.agarthavision.domain.usecase.capture.CaptureOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State holder for the Capture screen.
 *
 * Per ADR-005, the active session = the medtech's open smear. The screen has no
 * Start/Stop control any more; the picker creates sessions, and only [endSession]
 * closes one. Capture is medtech-triggered (Track 2.13): there is no auto-timer or
 * inference pause/resume sub-state any more — [onCapture] snapshots the cached frame
 * and saves it, queued for the background inference queue. Nothing on this screen waits on
 * a model, so there is no busy state (14zcqntj6nz).
 *
 * **Upstream collectors** (wired in `init`):
 * - [sessionManager].state → updates the active-session mirror in [CaptureState].
 * - [flaggedFrameStore].state → mirrors the queue into `flaggedFrames`.
 * - [networkMonitor].status → on `Disconnected`, latches `isConnectionLost = true`.
 *   The latch is cleared **only** by a successful [resumeConnection] probe (per
 *   CONTEXT.md).
 *
 * **Verification entry points:** [onCapturedFrameToastTap] (single-frame, from the
 * capture confirmation) opens the verification sheet directly. The queue lives on
 * its own route (`VerificationQueueScreen` + `VerificationQueueViewModel`); Capture
 * navigates there via a callback.
 *
 * See CONTEXT.md.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val flaggedFrameStore: FlaggedFrameStore,
    private val frameSampler: FrameSampler,
    private val networkMonitor: NetworkMonitor,
    private val captureFieldUseCase: CaptureFieldUseCase,
    private val clock: ElapsedClock,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 1)

    /** The cached frame the last tap saved, compared by identity. See [onCapture]. */
    private var lastSavedFrame: CachedFrame? = null

    /**
     * One-shot outcomes of a shutter tap, for the screen to confirm.
     *
     * An event per tap rather than a derived signal off [CaptureState.flaggedFrames]: the queue
     * is a live Room query and its head moves without anyone tapping anything. Verifying or
     * deleting a sample promotes an older row to the front, and re-entering the screen with a
     * queue already populated makes a months-old frame the newest thing the screen has seen.
     * Both used to fire a "Frame captured" confirmation for a frame nobody had just captured.
     */
    val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()


    init {
        // The /health poll runs only while a capture screen is mounted. Gating it on the
        // session instead would now mean polling forever, since sessions never end.
        networkMonitor.acquire()

        viewModelScope.launch {
            sessionManager.state.collect { sessionState ->
                _state.update { current ->
                    when (sessionState) {
                        SessionState.Idle -> current.copy(
                            activeSessionId = null,
                            activeSessionLabel = null,
                        )
                        is SessionState.Active -> current.copy(
                            activeSessionId = sessionState.session.sessionId,
                            activeSessionLabel = sessionState.session.label,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                _state.update { it.copy(flaggedFrames = frames) }
            }
        }

        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                if (status is NetworkMonitor.Status.Disconnected) {
                    _state.update { it.copy(isConnectionLost = true) }
                }
                // Do NOT clear isConnectionLost on Connected — only resumeConnection() success clears it
            }
        }
    }


    /**
     * Opens the sample a capture confirmation is about, looked up by the id that tap returned.
     *
     * By id, not by position: the confirmation stays on screen for a couple of seconds and the
     * queue keeps moving underneath it. A row that has already left the flagged set — verified
     * or deleted from elsewhere in that window — simply has nothing to open, which is why this
     * is a lookup that can miss rather than an index.
     */
    fun onCapturedFrameToastTap(sampleId: String) {
        val frame = _state.value.flaggedFrames.firstOrNull { it.sampleId == sampleId } ?: return
        _state.update { it.copy(verificationTarget = frame) }
    }

    fun onVerificationDismissed() {
        _state.update { it.copy(verificationTarget = null) }
    }

    /**
     * Snapshots the cached frame and saves it, queued for a model output that the background
     * inference queue produces later: cloud first, the on-device model as the fallback. Nothing
     * here waits on a model. See [CaptureFieldUseCase].
     *
     * The cached frame must be **fresh**, not merely present. `FrameSampler` is process-
     * scoped and its cache survives a session change, a screen exit and a camera rebind, so
     * a tap landing before the analyzer has delivered a frame for the current binding would
     * otherwise record the *previous* session's image under this session's id — and here a
     * session is a patient. Anything older than [MAX_FRAME_AGE_MS] is refused with the same
     * "waiting for a live frame" message as no frame at all; from the medtech's side the
     * two cases are one thing, "the camera isn't ready, tap again".
     */
    fun onCapture() {
        val sessionId = _state.value.activeSessionId
        if (sessionId == null) {
            _state.update { it.copy(errorMessage = "No active session available.") }
            return
        }
        val cached = frameSampler.latestFrame.value
        if (cached == null || clock.elapsedRealtimeMs() - cached.elapsedRealtimeMs > MAX_FRAME_AGE_MS) {
            _state.update { it.copy(errorMessage = "Waiting for a live frame.") }
            return
        }
        saveOnce(sessionId, cached)
    }

    /**
     * Saves [cached] unless the last tap already saved it.
     *
     * Nothing locks the shutter between taps any more, so a double tap would otherwise save the
     * one cached frame twice as two samples. A frame is saved once; the next tap waits for the
     * analyzer's next frame, which arrives well inside a deliberate second tap.
     */
    private fun saveOnce(sessionId: String, cached: CachedFrame) {
        if (cached === lastSavedFrame) return
        lastSavedFrame = cached
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null) }
            // The save takes milliseconds, so the screen no longer locks around it. It must
            // still finish if the medtech leaves in those milliseconds: a cancelled save would
            // drop a frame they saw the shutter take. Only the confirmation is skipped then.
            withContext(NonCancellable) { captureFieldUseCase(sessionId, cached.jpegBytes) }
                .onSuccess { outcome -> _events.emit(CaptureEvent.FrameCaptured(outcome)) }
                .onFailure { throwable ->
                    // Nothing was saved, so the same frame may be tried again.
                    if (lastSavedFrame === cached) lastSavedFrame = null
                    _state.update { it.copy(errorMessage = throwable.message ?: "Capture failed.") }
                }
        }
    }

    /**
     * Clears [CaptureState.errorMessage] once the screen has surfaced it (as a toast),
     * so the same error can fire again on the next tap.
     */
    override fun onCleared() {
        networkMonitor.release()
        super.onCleared()
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun resumeConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isProbingConnection = true) }
            val healthy = networkMonitor.probe()
            if (healthy) {
                _state.update { it.copy(isConnectionLost = false, isProbingConnection = false) }
            } else {
                _state.update { it.copy(isProbingConnection = false) }
            }
        }
    }

    private companion object {
        /**
         * How old a cached frame may be and still be captured.
         *
         * A bound analyzer delivers roughly 30 frames a second, so a genuinely live frame is
         * never more than about 33 ms old — a full second is unreachable while the camera is
         * running. Every stale path, meanwhile, leaves a gap far longer than this: a new
         * session, re-entering the screen, a camera rebind, a permission re-grant. So the
         * threshold costs nothing in normal use and fails closed in all of them.
         */
        private const val MAX_FRAME_AGE_MS = 1_000L
    }
}


/**
 * Immutable UI state surface for the Capture screen.
 *
 * @property activeSessionId Room sessionId of the active smear (null when idle).
 * @property activeSessionLabel the smear label entered in the picker, shown in
 *   the top app bar / REC badge area for orientation.
 * @property errorMessage transient error surfaced as a toast, then cleared via
 *   [CaptureViewModel.clearErrorMessage].
 * @property flaggedFrames mirror of [FlaggedFrameStore.state].
 * @property isConnectionLost latched true when [NetworkMonitor] reports
 *   `Disconnected`. NOT auto-cleared on reconnect — only [resumeConnection]
 *   success clears it.
 * @property isProbingConnection true while [resumeConnection] is awaiting a
 *   `/health` probe.
 * @property verificationTarget the frame currently being verified.
 */
data class CaptureState(
    val activeSessionId: String? = null,
    val activeSessionLabel: String? = null,
    val errorMessage: String? = null,
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val isConnectionLost: Boolean = false,
    val isProbingConnection: Boolean = false,
    val verificationTarget: FlaggedFrame? = null,
)

/**
 * A one-shot outcome of a shutter tap.
 *
 * Separate from [CaptureState] because a confirmation is an event, not a condition: it happens
 * once, to the medtech who tapped, and re-reading it later would be re-announcing it.
 */
sealed interface CaptureEvent {

    /**
     * The tap landed and a row was written.
     *
     * Fired for **every** successful capture, including the two that look like nothing happened:
     * a field the model read and found clean, and a field recorded while the inference container
     * was unreachable. Both are real results. Without a confirmation the medtech reads silence as
     * a missed tap and captures the same field again, which is how a smear ends up with duplicate
     * fields and an inflated count.
     */
    data class FrameCaptured(val outcome: CaptureOutcome) : CaptureEvent
}
