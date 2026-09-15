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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
 * Loads a persisted sample and its detection metadata.
 */
@HiltViewModel
class SampleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSampleDetailUseCase: GetSampleDetailUseCase,
    resolveSampleImageSourceUseCase: ResolveSampleImageSourceUseCase,
) : ViewModel() {
    private val sampleId: String = checkNotNull(savedStateHandle["sampleId"])

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
}
