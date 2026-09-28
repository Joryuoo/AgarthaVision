package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.usecase.records.GetSampleDetailUseCase
import com.agarthavision.domain.usecase.records.ResolveSampleImageSourceUseCase
import com.agarthavision.domain.usecase.records.SampleDetailResult
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.domain.usecase.verify.OpenVerificationTargetUseCase
import com.agarthavision.domain.usecase.verify.VerificationTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reason why a sample cannot be displayed on this device.
 */
enum class SampleUnavailable { NOT_FOUND, NOT_VISIBLE }

/**
 * UI state for one verified sample.
 */
data class SampleDetailState(
    val item: SampleRecordItem? = null,
    val itemResolved: Boolean = false,
    val unavailable: SampleUnavailable? = null,
    val imageSource: SampleImageSource = SampleImageSource.Unavailable(
        SampleImageUnavailableReason.SAMPLE_NOT_FOUND,
    ),
)

/**
 * What View Detection opens with: the sample, and the medtech's own previous answers.
 *
 * Held separately from [SampleDetailState] because it is loaded on demand rather than observed —
 * reopening a sample for editing is a decision, not a consequence of the screen being on screen.
 */
data class SampleEditState(
    val target: VerificationTarget? = null,
    val errorMessage: String? = null,
)

/**
 * Loads a persisted sample and its detection metadata.
 */
@HiltViewModel
class SampleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSampleDetailUseCase: GetSampleDetailUseCase,
    resolveSampleImageSourceUseCase: ResolveSampleImageSourceUseCase,
    private val openVerificationTarget: OpenVerificationTargetUseCase,
) : ViewModel() {
    private val sampleId: String = checkNotNull(savedStateHandle["sampleId"])

    private val _editState = MutableStateFlow(SampleEditState())
    val editState: StateFlow<SampleEditState> = _editState.asStateFlow()

    val state: StateFlow<SampleDetailState> = getSampleDetailUseCase(sampleId)
        .map { result ->
            val item = (result as? SampleDetailResult.Visible)?.data
            val unavail = when (result) {
                is SampleDetailResult.NotFound -> SampleUnavailable.NOT_FOUND
                is SampleDetailResult.NotVisible -> SampleUnavailable.NOT_VISIBLE
                else -> null
            }
            SampleDetailState(
                item = item,
                itemResolved = true,
                unavailable = unavail,
                imageSource = item?.let { resolveSampleImageSourceUseCase(it.sample) }
                    ?: SampleImageSource.Unavailable(SampleImageUnavailableReason.SAMPLE_NOT_FOUND),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SampleDetailState(),
        )

    /**
     * Opens the Verification Screen in edit mode.
     *
     * Through `OpenVerificationTargetUseCase`, which is the same path the verification queue
     * takes: it reconstructs the medtech's own previous answers from the detection rows, so an
     * edit is a correction rather than a re-review. Reopening with a blank questionnaire would
     * resubmit defaults over whatever they said the first time.
     */
    fun onViewDetection() {
        viewModelScope.launch {
            openVerificationTarget(sampleId).fold(
                onSuccess = { target -> _editState.update { it.copy(target = target, errorMessage = null) } },
                onFailure = { throwable -> _editState.update { it.copy(errorMessage = throwable.message) } },
            )
        }
    }

    /**
     * Closes edit mode.
     *
     * Nothing is written here, and nothing needs to be. A submission goes through
     * `SubmitVerificationUseCase`, which sets an already-SYNCED sample back to VERIFIED - and
     * that flip is what re-enters it into getSamplesPendingSync, guards it from being clobbered
     * by a pull (the E4 guard), and reaches Supabase as an upsert rather than a duplicate. There
     * is no second edit-tracking mechanism here, and there must not be.
     */
    fun onEditDismissed() {
        _editState.update { SampleEditState() }
    }
}
