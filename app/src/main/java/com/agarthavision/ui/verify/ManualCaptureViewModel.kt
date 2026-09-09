package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.SubmitManualCaptureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the manual capture verification sheet.
 */
data class ManualCaptureUiState(
    val frame: FlaggedFrame? = null,
    val frameIndexInQueue: Int = 0,
    val queueSize: Int = 0,
    val selectedSpecies: EggSpecies? = null,
    val otherSpeciesText: String = "",
    val userNote: String = "",
    val isRepeat: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = when (selectedSpecies) {
            null -> false
            EggSpecies.OTHER -> otherSpeciesText.isNotBlank() && !isSubmitting
            else -> !isSubmitting
        }

    /** False on the first frame of the queue, or when the position is unknown. */
    val canGoPrev: Boolean
        get() = frameIndexInQueue > 1

    /** False on the last frame of the queue, or when the position is unknown. */
    val canGoNext: Boolean
        get() = frameIndexInQueue in 1 until queueSize
}

/**
 * Events emitted from the manual capture sheet.
 */
sealed interface ManualCaptureEvent {
    data object Dismiss : ManualCaptureEvent
    data class ShowError(val message: String?) : ManualCaptureEvent
}

/**
 * State holder for [ManualSheet].
 *
 * Mirrors [VerificationViewModel]'s navigation surface so the two sheets page through
 * their queues identically — the same suppression applies for the same reason.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class ManualCaptureViewModel @Inject constructor(
    private val flaggedFrameStore: FlaggedFrameStore,
    private val submitManualCaptureUseCase: SubmitManualCaptureUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ManualCaptureUiState())
    val state: StateFlow<ManualCaptureUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ManualCaptureEvent>()
    val events: SharedFlow<ManualCaptureEvent> = _events.asSharedFlow()

    private var currentFrame: FlaggedFrame? = null

    init {
        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                val cycle = frames.manualFrames()
                val frame = currentFrame
                _state.update { current ->
                    current.copy(
                        queueSize = cycle.size,
                        frameIndexInQueue = positionOf(frame, cycle, current.frameIndexInQueue),
                    )
                }
            }
        }
    }

    /**
     * The frames this sheet cycles through: manual captures only.
     *
     * Model detections are reviewed in [VerificationSheet], which asks a different set
     * of questions, so paging onto one from here would render the wrong sheet — the host
     * picks the sheet from the frame it was opened with and never re-evaluates.
     */
    private fun List<FlaggedFrame>.manualFrames(): List<FlaggedFrame> =
        filter { it.source == FrameSource.MANUAL }

    /** The manual frames currently in the store, in queue order. */
    private fun cycleFrames(): List<FlaggedFrame> = flaggedFrameStore.state.value.manualFrames()

    /**
     * Position of [frame] by sample id. Matching on identity rather than equality keeps
     * navigation working regardless of how `FlaggedFrame` defines equals.
     */
    private fun List<FlaggedFrame>.indexOfSample(frame: FlaggedFrame): Int =
        indexOfFirst { it.sampleId == frame.sampleId }

    /**
     * 1-based position of [frame] within [frames], or [fallback] when it cannot be
     * located. Both [setFrame] and the store collector route through this so the counter
     * and the displayed frame cannot drift apart.
     */
    private fun positionOf(
        frame: FlaggedFrame?,
        frames: List<FlaggedFrame> = cycleFrames(),
        fallback: Int,
    ): Int {
        if (frame == null) return fallback
        val index = frames.indexOfSample(frame)
        return if (index >= 0) index + 1 else fallback
    }

    fun onFramePrev() {
        val frames = cycleFrames()
        val current = currentFrame ?: return
        val idx = frames.indexOfSample(current)
        if (idx <= 0) return
        setFrame(frames[idx - 1])
    }

    fun onFrameNext() {
        val frames = cycleFrames()
        val current = currentFrame ?: return
        val idx = frames.indexOfSample(current)
        if (idx < 0 || idx >= frames.size - 1) return
        setFrame(frames[idx + 1])
    }

    /**
     * Seeds the sheet with the selected manual-capture frame.
     */
    fun setFrame(frame: FlaggedFrame) {
        currentFrame = frame
        _state.update {
            it.copy(
                frame = frame,
                frameIndexInQueue = positionOf(frame, fallback = it.frameIndexInQueue),
                selectedSpecies = null,
                otherSpeciesText = "",
                userNote = "",
                isRepeat = frame.markedAsRepeat,
                isSubmitting = false,
                errorMessage = null,
            )
        }
    }

    /**
     * Updates the selected species in the manual sheet.
     */
    fun onSpeciesSelected(species: EggSpecies) {
        _state.update { it.copy(selectedSpecies = species, otherSpeciesText = "") }
    }

    /**
     * Updates the free-form "Other" species label.
     */
    fun onOtherSpeciesChanged(text: String) {
        _state.update { it.copy(otherSpeciesText = text) }
    }

    /**
     * Updates the optional user note value.
     */
    fun onUserNoteChanged(text: String) {
        _state.update { it.copy(userNote = text) }
    }

    /**
     * Toggles the repeat flag for the current frame.
     */
    fun onToggleRepeat() {
        val frame = currentFrame
        _state.update { it.copy(isRepeat = !it.isRepeat) }
        if (frame != null) {
            viewModelScope.launch {
                flaggedFrameStore.toggleRepeat(frame)
            }
        }
    }

    /**
     * Removes the current frame from the queue.
     */
    fun onDeleteFrame() {
        val frames = cycleFrames()
        val frame = currentFrame ?: return
        val idx = frames.indexOfSample(frame)
        // Pick replacement BEFORE removal: prefer the next frame, fall back to previous.
        val nextFrame = frames.getOrNull(idx + 1) ?: frames.getOrNull(idx - 1)
        viewModelScope.launch {
            flaggedFrameStore.remove(frame)
            if (nextFrame != null) {
                setFrame(nextFrame)
            } else {
                currentFrame = null
                _events.emit(ManualCaptureEvent.Dismiss)
            }
        }
    }

    /**
     * Persists the manual capture as a verified sample.
     */
    fun onSubmit() {
        val frame = currentFrame ?: return
        val snapshot = _state.value
        if (!snapshot.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            submitManualCaptureUseCase(
                frame = frame,
                species = snapshot.selectedSpecies,
                otherSpeciesText = snapshot.otherSpeciesText,
                userNote = snapshot.userNote,
                isRepeat = snapshot.isRepeat,
            ).fold(
                onSuccess = {
                    currentFrame = null
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(ManualCaptureEvent.Dismiss)
                },
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = error.message) }
                    _events.emit(ManualCaptureEvent.ShowError(error.message))
                },
            )
        }
    }

    /**
     * Dismisses the sheet without changes.
     */
    fun onCancel() {
        viewModelScope.launch { _events.emit(ManualCaptureEvent.Dismiss) }
    }
}
