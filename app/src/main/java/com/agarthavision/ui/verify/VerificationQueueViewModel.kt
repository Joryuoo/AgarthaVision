package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.FlaggedFrame
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
    /** Every row, and every row is unverified — there is no second list to filter out of this. */
    val samples: List<QueueSample> = emptyList(),
    /**
     * How many samples in this session have already been verified.
     *
     * Not rendered as a number. It is the only thing that separates the two empty states, which
     * an empty list of unverified rows cannot tell apart on its own.
     */
    val verifiedInSession: Int = 0,
    /** The session every row belongs to, carried in so the empty state still has a way onward. */
    val sessionId: String? = null,
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

    val verificationTarget: FlaggedFrame?
        get() = priorTarget?.frame
}

/** Which empty body to show when there is nothing left to verify. */
internal enum class QueueEmptyVariant { NEVER_HAD, ALL_DONE }

/**
 * Pure: pick the empty-state copy.
 *
 * An empty queue means one of two very different things to the medtech — nothing has been
 * captured yet, or every capture has been checked — and only the second deserves a completion
 * message and a way onward (86d4ayefd). The list itself is silent about which, because both look
 * like zero unverified rows; [verifiedInSession] is what distinguishes them.
 */
internal fun queueEmptyVariant(verifiedInSession: Int): QueueEmptyVariant =
    if (verifiedInSession > 0) QueueEmptyVariant.ALL_DONE else QueueEmptyVariant.NEVER_HAD

/**
 * State holder for the verification queue.
 *
 * **One flat list, no categories.** Every row is an unverified sample in the open session, held
 * locally and never pushed until a human submits it. Tapping a row opens the Verification Screen.
 *
 * This partially reverses 86d4ab4vm, deliberately: that ticket made the queue a union of verified
 * and unverified rows in two buckets so a verified sample stayed editable in place. That editing
 * now lives on the Sample Data Screen, reached from the Records screen's Samples tab, so the
 * queue goes back to meaning one thing — work still to do. The capability moved; it was not
 * dropped.
 *
 * Takes [ObserveVerificationQueueUseCase] rather than the data-layer `FlaggedFrameStore`. The two
 * now read the same rows, which is exactly why this must not be "simplified" back onto the store:
 * the store hands out `FlaggedFrame`, which carries the JPEG bytes of every row on every
 * emission, and it is a data-layer singleton this ViewModel was moved off on purpose (C1).
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
            observeVerificationQueue().collect { queue ->
                _state.update { current ->
                    // Prune ids that have left the list. A sample verified or deleted from
                    // elsewhere would otherwise leave a phantom in the count on the contextual
                    // bar — and verifying one is now the ordinary way a row leaves.
                    val live = queue.samples.mapTo(mutableSetOf()) { it.sampleId }
                    current.copy(
                        samples = queue.samples,
                        verifiedInSession = queue.verifiedInSession,
                        sessionId = queue.sessionId,
                        selectedIds = current.selectedIds intersect live,
                    )
                }
            }
        }
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
