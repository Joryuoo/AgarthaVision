package com.agarthavision.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.capture.CaptureFieldUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the Capture screen.
 *
 * Per ADR-005, the active session = the medtech's open smear. The screen has no
 * Start/Stop control any more; the picker creates sessions, and only [endSession]
 * closes one. Capture is medtech-triggered (Track 2.13): there is no auto-timer or
 * inference pause/resume sub-state any more — [onCapture] snapshots the cached frame
 * and runs inference exactly once per tap.
 *
 * **Upstream collectors** (wired in `init`):
 * - [sessionManager].state → updates the active-session mirror in [CaptureState].
 * - [flaggedFrameStore].state → mirrors the queue into `flaggedFrames`.
 * - [networkMonitor].status → on `Disconnected`, latches `isConnectionLost = true`.
 *   The latch is cleared **only** by a successful [resumeConnection] probe (per
 *   CONTEXT.md).
 *
 * **Verification entry points:** [onDetectionToastTap] (single-frame, from
 * Sonner) opens the verification sheet directly. The queue lives on its own
 * route (`VerificationQueueScreen` + `VerificationQueueViewModel`); Capture
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
        _state.update { it.copy(verificationTarget = frame) }
    }

    fun onVerificationDismissed() {
        _state.update { it.copy(verificationTarget = null) }
    }

    /**
     * Snapshots the cached frame and runs inference once. A server response records a
     * [com.agarthavision.domain.model.FrameSource.MODEL] frame (predictions may be
     * empty — a clean field is a normal negative result and is still recorded); an
     * [com.agarthavision.domain.usecase.inference.InferenceConnectionException]
     * records a [com.agarthavision.domain.model.FrameSource.MANUAL] frame instead.
     * See [CaptureFieldUseCase].
     */
    fun onCapture() {
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
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null) }
            captureFieldUseCase(sessionId, jpegBytes)
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message ?: "Capture failed.") }
                }
            _state.update { it.copy(isBusy = false) }
        }
    }

    /**
     * Clears [CaptureState.errorMessage] once the screen has surfaced it (as a toast),
     * so the same error can fire again on the next tap.
     */
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
}

/**
 * One-shot events emitted to the Capture composable.
 */
sealed interface CaptureEvent {
    data object SessionEnded : CaptureEvent
}

/**
 * Immutable UI state surface for the Capture screen.
 *
 * @property activeSessionId Room sessionId of the active smear (null when idle).
 * @property activeSessionLabel the smear label entered in the picker, shown in
 *   the top app bar / REC badge area for orientation.
 * @property isBusy true while End Session or a capture is in flight; hides the
 *   action button behind a progress spinner and blocks duplicate taps.
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
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val isConnectionLost: Boolean = false,
    val isProbingConnection: Boolean = false,
    val verificationTarget: FlaggedFrame? = null,
)
