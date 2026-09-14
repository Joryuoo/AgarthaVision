package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FrameSource
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
    REPEAT
}

internal fun filterQueueFrames(
    frames: List<FlaggedFrame>,
    filter: QueueFilter,
): List<FlaggedFrame> =
    frames.filter { frame ->
        when (filter) {
            QueueFilter.ALL -> true
            // Repeats are excluded so a duplicate cannot be verified by accident. They
            // stay reachable on purpose under REPEAT and ALL.
            QueueFilter.FLAGGED -> frame.source == FrameSource.MODEL && !frame.markedAsRepeat
            QueueFilter.MANUAL -> frame.source == FrameSource.MANUAL
            QueueFilter.REPEAT -> frame.markedAsRepeat
        }
    }

/** Which empty-state variant to show when the filtered frame list is empty. */
internal enum class QueueEmptyVariant { NEVER_HAD, FILTERED, ALL_DONE }

/**
 * Pure function: derive the correct empty-state variant from available counts.
 *
 * [filterActive] wins when a chip is hiding rows (even if frames were verified).
 * If no filter is active, a non-zero [verified] count means the session is done.
 * Otherwise the queue simply never had any items.
 */
internal fun queueEmptyVariant(
    verified: Int,
    filterActive: Boolean,
): QueueEmptyVariant = when {
    filterActive -> QueueEmptyVariant.FILTERED
    verified > 0 -> QueueEmptyVariant.ALL_DONE
    else -> QueueEmptyVariant.NEVER_HAD
}

data class VerificationQueueState(
    val flaggedFrames: List<FlaggedFrame> = emptyList(),
    val queueFilter: QueueFilter = QueueFilter.ALL,
    val verificationTarget: FlaggedFrame? = null,
    val verifiedCount: Int = 0,
    val activeSessionId: String? = null,
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
        viewModelScope.launch {
            flaggedFrameStore.verifiedCount.collect { count ->
                _state.update { it.copy(verifiedCount = count) }
            }
        }
        viewModelScope.launch {
            flaggedFrameStore.activeSessionId.collect { id ->
                _state.update { it.copy(activeSessionId = id) }
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
