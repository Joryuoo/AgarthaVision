package com.agarthavision.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the Capture screen.
 *
 * Owns the recording lifecycle (start/stop session via [SessionManager]) and the
 * surface of flagged frames awaiting verification ([FlaggedFrameStore]). Reacts
 * to inference-server reachability events from [NetworkMonitor] and latches a
 * "Cloud connection lost" banner until the user explicitly resumes.
 *
 * **Upstream collectors** (wired in `init`):
 * - [sessionManager].state → updates `isRecording` + `activeSessionId`.
 * - [flaggedFrameStore].state → mirrors the queue into `flaggedFrames`.
 * - [networkMonitor].status → on `Disconnected`, forces session stop and
 *   latches `isConnectionLost = true`. The latch is cleared **only** by a
 *   successful [resumeConnection] probe; reverting to `Connected` alone does
 *   not auto-clear it (see docs/03_MOBILE_APP_PLAN.md §1.9).
 *
 * **Verification entry points:** [onDetectionToastTap] (single-frame, from
 * Sonner) and [onQueueTap] / [onQueueItemSelected] (queue sheet). Both stop
 * the recording session as a side effect, per ADR-002 §UX.
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.1, §1.5, §1.6, §1.9.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val flaggedFrameStore: FlaggedFrameStore,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.state.collect { sessionState ->
                _state.update { current ->
                    when (sessionState) {
                        SessionState.Idle -> current.copy(isRecording = false, activeSessionId = null)
                        is SessionState.Recording -> current.copy(
                            isRecording = true,
                            activeSessionId = sessionState.session.sessionId,
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
                    if (_state.value.isRecording) runCatching { sessionManager.stopSession() }
                    _state.update { it.copy(isConnectionLost = true) }
                }
                // Do NOT clear isConnectionLost on Connected — only resumeConnection() success clears it
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                sessionManager.startSession()
            }.onFailure { throwable ->
                _state.update { it.copy(errorMessage = throwable.message) }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                sessionManager.stopSession()
            }.onFailure { throwable ->
                _state.update { it.copy(errorMessage = throwable.message) }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun onDetectionToastTap(frame: FlaggedFrame) {
        viewModelScope.launch {
            if (_state.value.isRecording) runCatching { sessionManager.stopSession() }
            _state.update { it.copy(verificationTarget = frame) }
        }
    }

    fun onVerificationDismissed() {
        _state.update { it.copy(verificationTarget = null) }
    }

    fun onQueueTap() {
        viewModelScope.launch {
            if (_state.value.isRecording) runCatching { sessionManager.stopSession() }
            _state.update { it.copy(isQueueOpen = true) }
        }
    }

    fun onQueueDismiss() {
        _state.update { it.copy(isQueueOpen = false) }
    }

    fun onQueueItemSelected(frame: FlaggedFrame) {
        _state.update { it.copy(isQueueOpen = false, verificationTarget = frame) }
    }

    fun onQueueItemDeleted(frame: FlaggedFrame) {
        flaggedFrameStore.remove(frame)
    }

    fun resumeConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isProbingConnection = true) }
            val healthy = networkMonitor.probe()
            if (healthy) {
                _state.update { it.copy(isConnectionLost = false, isProbingConnection = false) }
                runCatching { sessionManager.startSession() }
            } else {
                _state.update { it.copy(isProbingConnection = false) }
            }
        }
    }
}

/**
 * Immutable UI state surface for the Capture screen.
 *
 * @property isRecording true while the active [SessionManager] state is `Recording`.
 *   Drives the REC indicator and the Start/Stop button label.
 * @property isBusy true while a start/stop call is in flight; hides the action
 *   button behind a progress spinner.
 * @property activeSessionId the Room sessionId of the in-flight session (null
 *   when idle).
 * @property errorMessage transient error surfaced under the action button
 *   (e.g. "no Supabase session" on start failure).
 * @property flaggedFrames mirror of [FlaggedFrameStore.state] for the badge
 *   count and queue sheet. Cleared on logout / new session.
 * @property isConnectionLost latched true when [NetworkMonitor] reports
 *   `Disconnected`. NOT auto-cleared on reconnect — only [resumeConnection]
 *   success clears it. While true the inline destructive banner shows.
 * @property isProbingConnection true while [resumeConnection] is awaiting a
 *   `/health` probe; drives the spinner in the banner's Resume button.
 * @property verificationTarget the frame currently being verified, set by
 *   either toast tap or queue-row tap. Non-null means [VerificationSheet]
 *   is mounted.
 * @property isQueueOpen true means the [VerificationQueueSheet] is mounted.
 *   Set by [onQueueTap]; cleared by [onQueueDismiss] or [onQueueItemSelected].
 */
data class CaptureState(
    val isRecording: Boolean = false,
    val isBusy: Boolean = false,
    val activeSessionId: String? = null,
    val errorMessage: String? = null,
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val isConnectionLost: Boolean = false,
    val isProbingConnection: Boolean = false,
    val verificationTarget: FlaggedFrame? = null,
    val isQueueOpen: Boolean = false,
)
