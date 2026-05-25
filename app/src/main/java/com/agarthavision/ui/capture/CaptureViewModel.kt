package com.agarthavision.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * ViewModel boundary for the continuous-capture screen.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val flaggedFrameStore: FlaggedFrameStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureState())

    /**
     * Screen state consumed by `CaptureScreen`.
     */
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    init {
        // Observe Session State
        viewModelScope.launch {
            sessionManager.state.collect { sessionState ->
                _state.update { current ->
                    when (sessionState) {
                        SessionState.Idle -> current.copy(
                            isRecording = false,
                            activeSessionId = null,
                        )
                        is SessionState.Recording -> current.copy(
                            isRecording = true,
                            activeSessionId = sessionState.session.sessionId,
                        )
                    }
                }
            }
        }

        // Observe Flagged Frames
        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                _state.update { it.copy(flaggedFrames = frames) }
            }
        }
    }

    /**
     * Starts a new recording session.
     */
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

    /**
     * Stops the active recording session.
     */
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
}

/**
 * State for the capture screen's session controls.
 */
data class CaptureState(
    /**
     * Whether a recording session is currently active.
     */
    val isRecording: Boolean = false,

    /**
     * Whether a start or stop request is currently running.
     */
    val isBusy: Boolean = false,

    /**
     * Identifier of the active session, if recording.
     */
    val activeSessionId: String? = null,

    /**
     * Latest session-control error message, if any.
     */
    val errorMessage: String? = null,

    /**
     * Frames that have been flagged by inference but not yet verified.
     */
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
)
