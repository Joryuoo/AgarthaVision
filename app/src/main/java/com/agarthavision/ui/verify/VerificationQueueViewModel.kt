package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.QueueBucket
import com.agarthavision.domain.model.QueueSample
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
    val errorMessage: String? = null,
) {
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
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationQueueState())
    val state: StateFlow<VerificationQueueState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeVerificationQueue().collect { samples ->
                _state.update { it.copy(samples = samples) }
            }
        }
    }

    fun onBucketSelected(bucket: QueueBucket) {
        _state.update { it.copy(bucket = bucket) }
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
