package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase
import com.agarthavision.domain.usecase.records.GetSessionSamplesUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportsUseCase
import com.agarthavision.domain.usecase.records.SessionSamples
import com.agarthavision.domain.usecase.reports.SessionEggCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for one session's verified samples + persisted reports.
 */
data class SessionDetailState(
    val session: SessionSamples? = null,
    val eggCounts: List<EggCountSummary> = emptyList(),
    val totalEggCount: Int = 0,
    val epg: Int = 0,
    val reports: List<Report> = emptyList(),
    val isGenerating: Boolean = false,
    val generationError: String? = null,
)

/**
 * Display-ready egg count entry for the session detail screen.
 */
data class EggCountSummary(
    val species: String,
    val count: Int,
)

/**
 * One-shot UI events emitted by the session detail screen.
 */
sealed interface SessionDetailEvent {
    /**
     * A report was successfully generated and saved at [csvPath].
     */
    data class ReportGenerated(val csvPath: String) : SessionDetailEvent
}

/**
 * Loads session samples + reports and triggers report generation.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSessionSamplesUseCase: GetSessionSamplesUseCase,
    observeSessionReportsUseCase: ObserveSessionReportsUseCase,
    private val sessionEggCountUseCase: SessionEggCountUseCase,
    private val generateSessionReportUseCase: GenerateSessionReportUseCase,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val generationState = MutableStateFlow(GenerationState())

    private val _events = MutableSharedFlow<SessionDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionDetailEvent> = _events.asSharedFlow()

    val state: StateFlow<SessionDetailState> = combine(
        getSessionSamplesUseCase(sessionId),
        observeSessionReportsUseCase(sessionId),
        generationState,
    ) { session, reports, generation ->
        val eggCounts = sessionEggCountUseCase(sessionId)
        SessionDetailState(
            session = session,
            eggCounts = eggCounts.counts.map { EggCountSummary(it.species, it.count) },
            totalEggCount = eggCounts.totalEggCount,
            epg = eggCounts.epg,
            reports = reports,
            isGenerating = generation.isGenerating,
            generationError = generation.error,
        )
    }
        .mapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailState(),
        )

    /**
     * Generates a fresh report for this session. Emits [SessionDetailEvent.ReportGenerated]
     * on success so the screen can offer the medtech a share action.
     */
    fun generateReport() {
        viewModelScope.launch {
            generationState.update { it.copy(isGenerating = true, error = null) }
            generateSessionReportUseCase(sessionId).fold(
                onSuccess = { report ->
                    generationState.update { GenerationState() }
                    report.csvFilePath?.let { _events.emit(SessionDetailEvent.ReportGenerated(it)) }
                },
                onFailure = { error ->
                    generationState.update {
                        GenerationState(error = error.message ?: error::class.simpleName)
                    }
                },
            )
        }
    }
}

private data class GenerationState(
    val isGenerating: Boolean = false,
    val error: String? = null,
)
