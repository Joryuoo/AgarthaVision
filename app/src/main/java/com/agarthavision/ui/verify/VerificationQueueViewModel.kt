package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QueueFilter {
    ALL,
    FLAGGED,
    MANUAL,
    REPEAT,
}

data class VerificationQueueState(
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val queueFilter: QueueFilter = QueueFilter.ALL,
    val verificationTarget: FlaggedFrame? = null,
)

@HiltViewModel
class VerificationQueueViewModel @Inject constructor(
    private val flaggedFrameStore: FlaggedFrameStore,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationQueueState())
    val state: StateFlow<VerificationQueueState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                _state.update { it.copy(flaggedFrames = frames) }
            }
        }
    }

    fun onQueueFilterSelected(filter: QueueFilter) {
        _state.update { it.copy(queueFilter = filter) }
    }

    fun onQueueItemSelected(frame: FlaggedFrame) {
        _state.update { it.copy(verificationTarget = frame) }
    }

    fun onQueueItemDeleted(frame: FlaggedFrame) {
        viewModelScope.launch {
            flaggedFrameStore.remove(frame)
        }
    }

    fun onVerificationDismissed() {
        _state.update { it.copy(verificationTarget = null) }
    }
}
