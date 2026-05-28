package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
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
 */
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

    /**
     * Seeds the sheet with the selected manual-capture frame.
     */
    fun setFrame(frame: FlaggedFrame) {
        currentFrame = frame
        _state.update {
            it.copy(
                frame = frame,
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
        val frame = currentFrame ?: return
        viewModelScope.launch {
            flaggedFrameStore.remove(frame)
            currentFrame = null
            _events.emit(ManualCaptureEvent.Dismiss)
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
