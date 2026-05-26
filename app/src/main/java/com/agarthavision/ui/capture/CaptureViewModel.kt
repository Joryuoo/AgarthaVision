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

data class CaptureState(
    val isRecording: Boolean = false,
    val isBusy: Boolean = false,
    val activeSessionId: String? = null,
    val errorMessage: String? = null,
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val isConnectionLost: Boolean = false,
    val isProbingConnection: Boolean = false,
    val verificationTarget: FlaggedFrame? = null,
)
