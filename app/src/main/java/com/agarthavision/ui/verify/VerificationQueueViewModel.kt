package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.QueueBucket
import com.agarthavision.domain.model.QueueSample
import com.agarthavision.domain.usecase.verify.DeleteQueueItemsUseCase
import com.agarthavision.domain.usecase.verify.ObserveVerificationQueueUseCase
import com.agarthavision.domain.usecase.verify.OpenVerificationTargetUseCase
import com.agarthavision.domain.usecase.verify.VerificationTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerificationQueueState(
    val samples: List<QueueSample> = emptyList(),
    val bucket: QueueBucket = QueueBucket.UNVERIFIED,
    /**
     * The sample open in the sheet, with whatever the medtech already said about it.
     *
     * One object rather than a frame plus three loose fields, so the answers cannot end up
     * describing a different sample than the one on screen.
     */
    val priorTarget: VerificationTarget? = null,
    /**
     * Sample ids picked out for deletion, **by id rather than by object**.
     *
     * The same trap as the list key, one layer up: holding rows would mean a re-emission that
     * changed a selected sample silently dropped it out of the selection.
     */
    val selectedIds: Set<String> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Derived, never stored, so the flag and the set cannot disagree. */
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()

    /** How the selection splits, which is what the confirmation dialog has to say out loud. */
    val selectedVerifiedCount: Int
        get() = samples.count { it.sampleId in selectedIds && it.isVerified }

    val selectedUnverifiedCount: Int
        get() = samples.count { it.sampleId in selectedIds && !it.isVerified }

    val verificationTarget: FlaggedFrame?
        get() = priorTarget?.frame

    /** The rows in the selected bucket. */
    val visibleSamples: List<QueueSample>
        get() = samples.filter { it.bucket == bucket }

    /**
     * Counts per bucket, derived from the same list the rows come from.
     *
     * A row belongs to exactly one bucket by construction, so a count cannot disagree with its
     * list — which is what the previous version had to defend against by routing its chip counts
     * through the same filter function the list used.
     */
    val counts: Map<QueueBucket, Int>
        get() = samples.groupingBy { it.bucket }.eachCount()
}

/**
 * State holder for the verification queue.
 *
 * Shows the **union** of verified and unverified samples for the open session. Verified samples
 * stay visible and stay editable: tapping one reopens it with the medtech's own previous answers
 * so a mistake can be corrected rather than lived with.
 *
 * Takes [ObserveVerificationQueueUseCase] rather than the data-layer `FlaggedFrameStore`, which
 * only ever held the unverified half — and which takes this ViewModel back inside C1.
 */
@HiltViewModel
class VerificationQueueViewModel @Inject constructor(
    observeVerificationQueue: ObserveVerificationQueueUseCase,
    private val openVerificationTarget: OpenVerificationTargetUseCase,
    private val deleteQueueItems: DeleteQueueItemsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationQueueState())
    val state: StateFlow<VerificationQueueState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeVerificationQueue().collect { samples ->
                _state.update { current ->
                    // Prune ids that have left the list. A sample deleted from elsewhere would
                    // otherwise leave a phantom in the count on the contextual bar.
                    val live = samples.mapTo(mutableSetOf()) { it.sampleId }
                    current.copy(
                        samples = samples,
                        selectedIds = current.selectedIds intersect live,
                    )
                }
            }
        }
    }

    fun onBucketSelected(bucket: QueueBucket) {
        // Clears the selection: confirming a delete of rows you can no longer see is exactly
        // the kind of mistake an irreversible action must not allow.
        _state.update { it.copy(bucket = bucket, selectedIds = emptySet()) }
    }

    /** Long-press starts selection; tapping while selecting adds and removes. */
    fun onToggleSelected(sample: QueueSample) {
        _state.update { current ->
            val next = if (sample.sampleId in current.selectedIds) {
                current.selectedIds - sample.sampleId
            } else {
                current.selectedIds + sample.sampleId
            }
            current.copy(selectedIds = next)
        }
    }

    fun onClearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    fun onDeleteRequested() {
        if (_state.value.selectedIds.isNotEmpty()) {
            _state.update { it.copy(showDeleteConfirm = true) }
        }
    }

    fun onDeleteDismissed() {
        _state.update { it.copy(showDeleteConfirm = false) }
    }

    fun onDeleteConfirmed() {
        val ids = _state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            deleteQueueItems(ids).fold(
                onSuccess = {
                    _state.update {
                        it.copy(selectedIds = emptySet(), showDeleteConfirm = false)
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(showDeleteConfirm = false, errorMessage = throwable.message)
                    }
                },
            )
        }
    }

    fun onQueueItemSelected(sample: QueueSample) {
        viewModelScope.launch {
            openVerificationTarget(sample.sampleId).fold(
                onSuccess = { target ->
                    _state.update { it.copy(priorTarget = target, errorMessage = null) }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                },
            )
        }
    }

    fun onVerificationDismissed() {
        _state.update { it.copy(priorTarget = null) }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }
}
