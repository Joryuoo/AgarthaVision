package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state surface for the verification flow.
 *
 * Tracks both the current frame under review and per-detection answers within
 * that frame. The verdict model (Q1→Q2→Q3 branching + frame-level Q4) is
 * documented in [ADR-004](../../../../../../../../docs/adr/004-verification-as-hitl-correction.md).
 *
 * @property isVisible whether the sheet is currently mounted.
 * @property frameIndexInQueue 1-based position of [frame] in `FlaggedFrameStore`.
 * @property queueSize total number of flagged frames in the store.
 * @property frame the frame currently being verified.
 * @property currentDetectionIndex which detection within [frame] is highlighted.
 * @property showBoundingBoxes toggle for the box overlay on the frame image.
 * @property answers per-detection answers (one entry per box in `frame.predictions`).
 * @property missedEgg frame-level Q4 answer — sets `samples.needs_reannotation`.
 * @property isSubmitting true while [SubmitVerificationUseCase] is in flight.
 * @property errorMessage submission failure message; surfaced inline.
 * @property canSubmit derived — true when every per-detection answer is complete
 *   and we're not already submitting.
 */
data class VerificationUiState(
    val isVisible: Boolean = false,
    val frameIndexInQueue: Int = 0,
    val queueSize: Int = 0,
    val frame: FlaggedFrame? = null,
    val currentDetectionIndex: Int = 0,
    val showBoundingBoxes: Boolean = true,
    val answers: List<VerificationAnswers> = emptyList(),
    val missedEgg: Boolean? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = answers.isNotEmpty() && answers.all { it.isComplete } && !isSubmitting
}

sealed interface VerificationEvent {
    data object Dismiss : VerificationEvent
    data class ShowError(val message: String?) : VerificationEvent
}

/**
 * State holder for the [VerificationSheet].
 *
 * Owns:
 * - **Frame-level navigation** across the [FlaggedFrameStore] queue
 *   ([onFramePrev], [onFrameNext], [onDeleteFrame]).
 * - **Detection-level navigation** within the current frame
 *   ([onDetectionPrev], [onDetectionNext]) and per-detection answers
 *   ([onQ1Selected], [onQ2Selected], [onSpeciesSelected], [onOtherSpeciesChanged]).
 * - **Frame-level Q4** ("did the model miss any eggs?", via [onQ4Selected]).
 * - **Submit** orchestration through [SubmitVerificationUseCase] — on success
 *   the frame is removed from the store; the verdict model (per ADR-004)
 *   persists every detection regardless of mix (false positives, wrong
 *   class, box-incorrect) so the dataset captures labeled corrections.
 *
 * The store collector (`init`) keeps `queueSize` + `frameIndexInQueue` in sync
 * as frames are added/removed by other surfaces (Capture toast/queue, delete).
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.6 + ADR-004.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val flaggedFrameStore: FlaggedFrameStore,
    private val submitVerificationUseCase: SubmitVerificationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VerificationEvent>()
    val events: SharedFlow<VerificationEvent> = _events.asSharedFlow()

    private var currentFrame: FlaggedFrame? = null

    init {
        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                val frame = currentFrame
                _state.update { current ->
                    current.copy(
                        queueSize = frames.size,
                        frameIndexInQueue = if (frame != null) {
                            val idx = frames.indexOf(frame)
                            if (idx >= 0) idx + 1 else current.frameIndexInQueue
                        } else current.frameIndexInQueue,
                    )
                }
            }
        }
    }

    fun setFrame(frame: FlaggedFrame) {
        currentFrame = frame
        _state.update {
            it.copy(
                isVisible = true,
                frame = frame,
                currentDetectionIndex = 0,
                answers = List(frame.predictions.size) { VerificationAnswers() },
                missedEgg = null,
                isSubmitting = false,
                errorMessage = null,
            )
        }
    }

    fun onQ1Selected(isEgg: Boolean) {
        updateCurrentAnswer { it.copy(isEgg = isEgg, isBoxCorrect = null, species = null, otherSpeciesText = "") }
    }

    fun onQ2Selected(isBoxCorrect: Boolean) {
        updateCurrentAnswer { it.copy(isBoxCorrect = isBoxCorrect, species = null, otherSpeciesText = "") }
    }

    fun onSpeciesSelected(species: EggSpecies) {
        updateCurrentAnswer { it.copy(species = species, otherSpeciesText = "") }
    }

    fun onOtherSpeciesChanged(text: String) {
        updateCurrentAnswer { it.copy(otherSpeciesText = text) }
    }

    fun onQ4Selected(missedEgg: Boolean) {
        _state.update { it.copy(missedEgg = missedEgg) }
    }

    fun onDetectionPrev() {
        _state.update { current ->
            current.copy(currentDetectionIndex = (current.currentDetectionIndex - 1).coerceAtLeast(0))
        }
    }

    fun onDetectionNext() {
        _state.update { current ->
            val maxIndex = (current.frame?.predictions?.size ?: 1) - 1
            current.copy(currentDetectionIndex = (current.currentDetectionIndex + 1).coerceAtMost(maxIndex))
        }
    }

    fun onFramePrev() {
        val frames = flaggedFrameStore.state.value
        val current = currentFrame ?: return
        val idx = frames.indexOf(current)
        if (idx <= 0) return
        setFrame(frames[idx - 1])
    }

    fun onFrameNext() {
        val frames = flaggedFrameStore.state.value
        val current = currentFrame ?: return
        val idx = frames.indexOf(current)
        if (idx < 0 || idx >= frames.size - 1) return
        setFrame(frames[idx + 1])
    }

    fun onDeleteFrame() {
        val frames = flaggedFrameStore.state.value
        val current = currentFrame ?: return
        val idx = frames.indexOf(current)
        // Pick replacement BEFORE removal: prefer the next frame, fall back to previous.
        val nextFrame = frames.getOrNull(idx + 1) ?: frames.getOrNull(idx - 1)
        flaggedFrameStore.remove(current)
        if (nextFrame != null) {
            setFrame(nextFrame)
        } else {
            currentFrame = null
            viewModelScope.launch { _events.emit(VerificationEvent.Dismiss) }
        }
    }

    fun onToggleBoundingBoxes() {
        _state.update { it.copy(showBoundingBoxes = !it.showBoundingBoxes) }
    }

    fun onSubmit() {
        val frame = currentFrame ?: return
        val snapshot = _state.value
        if (!snapshot.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            submitVerificationUseCase(
                frame = frame,
                answers = snapshot.answers,
                missedEgg = snapshot.missedEgg,
            ).fold(
                onSuccess = {
                    currentFrame = null
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(VerificationEvent.Dismiss)
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = throwable.message) }
                    _events.emit(VerificationEvent.ShowError(throwable.message))
                },
            )
        }
    }

    fun onCancel() {
        viewModelScope.launch { _events.emit(VerificationEvent.Dismiss) }
    }

    private fun updateCurrentAnswer(transform: (VerificationAnswers) -> VerificationAnswers) {
        val index = _state.value.currentDetectionIndex
        _state.update { current ->
            val updated = current.answers.toMutableList()
            if (index in updated.indices) updated[index] = transform(updated[index])
            current.copy(answers = updated)
        }
    }
}
