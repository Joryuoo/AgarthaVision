package com.agarthavision.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.FlaggedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * State holder for the Capture screen.
 *
 * Per ADR-005, the active session = the medtech's open smear. The screen has no
 * Start/Stop control any more; the picker creates sessions, and only [endSession]
 * closes one. Inference auto-pauses/resumes via [SessionManager.pauseInference]
 * (Track 2.9 wires the lifecycle observers).
 *
 * **Upstream collectors** (wired in `init`):
 * - [sessionManager].state → updates the active-session mirror in [CaptureState].
 * - [flaggedFrameStore].state → mirrors the queue into `flaggedFrames`.
 * - [networkMonitor].status → on `Disconnected`, pauses inference and latches
 *   `isConnectionLost = true`. The latch is cleared **only** by a successful
 *   [resumeConnection] probe (per docs/03_MOBILE_APP_PLAN.md §1.9).
 *
 * **Verification entry points:** [onDetectionToastTap] (single-frame, from
 * Sonner) and [onQueueTap] / [onQueueItemSelected] (queue sheet). Each pauses
 * inference while the sheet is mounted — they no longer end the session.
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.1, §1.5, §1.6, §1.9.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val flaggedFrameStore: FlaggedFrameStore,
    private val frameSampler: FrameSampler,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val eventChannel = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionManager.state.collect { sessionState ->
                _state.update { current ->
                    when (sessionState) {
                        SessionState.Idle -> current.copy(
                            activeSessionId = null,
                            activeSessionLabel = null,
                            isInferenceRunning = false,
                        )
                        is SessionState.Active -> current.copy(
                            activeSessionId = sessionState.session.sessionId,
                            activeSessionLabel = sessionState.session.label,
                            isInferenceRunning = sessionState.isInferenceRunning,
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
                    sessionManager.pauseInference()
                    _state.update { it.copy(isConnectionLost = true) }
                }
                // Do NOT clear isConnectionLost on Connected — only resumeConnection() success clears it
            }
        }
    }

    /**
     * Closes the active smear session, persists optional [notes], and emits a
     * navigate-to-picker event.
     */
    fun endSession(notes: String?) {
        val cleanNotes = notes?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                sessionManager.stopSession(notes = cleanNotes)
            }.onSuccess {
                eventChannel.send(CaptureEvent.SessionEnded)
            }.onFailure { throwable ->
                _state.update { it.copy(errorMessage = throwable.message) }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun onDetectionToastTap(frame: FlaggedFrame) {
        sessionManager.pauseInference()
        _state.update { it.copy(verificationTarget = frame) }
    }

    fun onVerificationDismissed() {
        sessionManager.resumeInference()
        _state.update { it.copy(verificationTarget = null) }
    }

    fun onQueueTap() {
        sessionManager.pauseInference()
        _state.update { it.copy(isQueueOpen = true) }
    }

    fun onQueueDismiss() {
        sessionManager.resumeInference()
        _state.update { it.copy(isQueueOpen = false) }
    }

    fun onQueueItemSelected(frame: FlaggedFrame) {
        _state.update { it.copy(isQueueOpen = false, verificationTarget = frame) }
    }

    fun onQueueItemDeleted(frame: FlaggedFrame) {
        flaggedFrameStore.remove(frame)
    }

    /**
     * Captures a manual snapshot of the current camera feed and adds it to the queue.
     */
    fun onManualCapture() {
        val sessionId = _state.value.activeSessionId
        if (sessionId == null) {
            _state.update { it.copy(errorMessage = "No active session available.") }
            return
        }
        val jpegBytes = frameSampler.latestFrameBytes.value
        if (jpegBytes == null) {
            _state.update { it.copy(errorMessage = "Waiting for a live frame.") }
            return
        }
        flaggedFrameStore.add(
            FlaggedFrame(
                sessionId = sessionId,
                capturedAt = Instant.now(),
                jpegBytes = jpegBytes,
                predictions = emptyList(),
                source = FrameSource.MANUAL,
                inferenceModelVersion = null,
                imageWidth = null,
                imageHeight = null,
            ),
        )
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Toggles the queue-row Repeat marker on [frame] (per ADR-005). The marker
     * pre-loads `VerificationViewModel.isRepeat` when the medtech opens the row.
     */
    fun onQueueItemToggleRepeat(frame: FlaggedFrame) {
        flaggedFrameStore.toggleRepeat(frame)
    }

    fun onQueueFilterSelected(filter: QueueFilter) {
        _state.update { it.copy(queueFilter = filter) }
    }

    /**
     * Pause/resume hooks exposed to the screen so a [LifecycleEventObserver]
     * can toggle inference when Capture goes to background/foreground.
     */
    fun pauseInference() {
        sessionManager.pauseInference()
    }

    fun resumeInferenceIfNoOverlay() {
        // Only resume when nothing is on top — otherwise the user just closed the
        // app while a sheet was open, and the sheet's own dismiss handler should drive resume.
        val s = _state.value
        if (s.verificationTarget == null && !s.isQueueOpen && !s.isConnectionLost) {
            sessionManager.resumeInference()
        }
    }

    fun resumeConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isProbingConnection = true) }
            val healthy = networkMonitor.probe()
            if (healthy) {
                _state.update { it.copy(isConnectionLost = false, isProbingConnection = false) }
                sessionManager.resumeInference()
            } else {
                _state.update { it.copy(isProbingConnection = false) }
            }
        }
    }
}

/**
 * One-shot events emitted to the Capture composable.
 */
sealed interface CaptureEvent {
    data object SessionEnded : CaptureEvent
}

/**
 * Filter chip selection on the [VerificationQueueSheet]. Per ADR-005 / plan §6.7.
 */
enum class QueueFilter {
    ALL,
    FLAGGED,
    MANUAL,
    REPEAT,
}

/**
 * Immutable UI state surface for the Capture screen.
 *
 * @property activeSessionId Room sessionId of the active smear (null when idle).
 * @property activeSessionLabel the smear label entered in the picker, shown in
 *   the top app bar / REC badge area for orientation.
 * @property isInferenceRunning mirrors [SessionState.Active.isInferenceRunning].
 *   Drives the REC indicator and gates the inference pipeline.
 * @property isBusy true while End Session is in flight; hides the action button
 *   behind a progress spinner.
 * @property errorMessage transient error surfaced under the action button.
 * @property flaggedFrames mirror of [FlaggedFrameStore.state].
 * @property isConnectionLost latched true when [NetworkMonitor] reports
 *   `Disconnected`. NOT auto-cleared on reconnect — only [resumeConnection]
 *   success clears it.
 * @property isProbingConnection true while [resumeConnection] is awaiting a
 *   `/health` probe.
 * @property verificationTarget the frame currently being verified.
 * @property isQueueOpen true means the [VerificationQueueSheet] is mounted.
 */
data class CaptureState(
    val activeSessionId: String? = null,
    val activeSessionLabel: String? = null,
    val isInferenceRunning: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val isConnectionLost: Boolean = false,
    val isProbingConnection: Boolean = false,
    val verificationTarget: FlaggedFrame? = null,
    val isQueueOpen: Boolean = false,
    val queueFilter: QueueFilter = QueueFilter.ALL,
)
