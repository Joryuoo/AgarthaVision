package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.usecase.records.GetSampleDetailUseCase
import com.agarthavision.domain.usecase.records.SampleRecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * UI state for one verified sample.
 */
data class SampleDetailState(
    val item: SampleRecordItem? = null,
)

/**
 * Loads a persisted sample and its detection metadata.
 */
@HiltViewModel
class SampleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSampleDetailUseCase: GetSampleDetailUseCase,
) : ViewModel() {
    private val sampleId: String = checkNotNull(savedStateHandle["sampleId"])

    val state: StateFlow<SampleDetailState> = getSampleDetailUseCase(sampleId)
        .map { item -> SampleDetailState(item = item) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SampleDetailState(),
        )
}
