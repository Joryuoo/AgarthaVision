package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.usecase.records.ExportSessionUseCase
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.domain.usecase.records.SessionSamples
import com.agarthavision.domain.usecase.records.GetSessionSamplesUseCase
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
 * UI state for one session's verified samples.
 */
data class SessionDetailState(
    val session: SessionSamples? = null,
    val isExporting: Boolean = false,
    val exportPath: String? = null,
    val exportError: String? = null,
)

/**
 * Loads session samples and exports the raw session CSV.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSessionSamplesUseCase: GetSessionSamplesUseCase,
    private val exportSessionUseCase: ExportSessionUseCase,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val exportState = MutableStateFlow(ExportState())

    val state: StateFlow<SessionDetailState> = getSessionSamplesUseCase(sessionId)
        .map { session ->
            val export = exportState.value
            SessionDetailState(
                session = session,
                isExporting = export.isExporting,
                exportPath = export.exportPath,
                exportError = export.exportError,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailState(),
        )

    /**
     * Observable export state for Compose to recompose while the report is writing.
     */
    val currentExportState: StateFlow<ExportState> = exportState.asStateFlow()

    /**
     * Generates and writes a CSV export for this session.
     */
    fun exportCsv() {
        viewModelScope.launch {
            exportState.update { it.copy(isExporting = true, exportPath = null, exportError = null) }
            val result = exportSessionUseCase(sessionId)
            exportState.update {
                result.fold(
                    onSuccess = { path -> ExportState(exportPath = path) },
                    onFailure = { error -> ExportState(exportError = error.message ?: error::class.simpleName) },
                )
            }
        }
    }
}

/**
 * State for the asynchronous CSV export operation.
 */
data class ExportState(
    val isExporting: Boolean = false,
    val exportPath: String? = null,
    val exportError: String? = null,
)
