package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.InfectivityLevel
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportFormat
import com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase
import com.agarthavision.domain.usecase.records.GetSessionSamplesUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportCountUseCase
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
import kotlinx.coroutines.flow.flatMapLatest
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
    val infectivityLevel: InfectivityLevel? = null,
    val infectivitySpeciesLabel: String? = null,
    val reports: List<Report> = emptyList(),
    val totalReports: Int = 0,
    val currentPage: Int = 0,
    val isGenerating: Boolean = false,
    val generationError: String? = null,
)

/** Reports shown per page; more than this paginate via the Prev/Next pager. */
const val REPORTS_PER_PAGE: Int = 5

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
     * A report was successfully generated. Both [pdfPath] and [csvPath] are the files the use
     * case wrote for it (either may be null if that file failed to write); [format] is the
     * export the medtech chose from the generate menu, so the snackbar's Share action shares
     * the file that matches what they asked for rather than always the PDF.
     */
    data class ReportGenerated(
        val pdfPath: String?,
        val csvPath: String?,
        val format: ExportFormat,
    ) : SessionDetailEvent
}

/**
 * Loads session samples + reports and triggers report generation.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getSessionSamplesUseCase: GetSessionSamplesUseCase,
    observeSessionReportsUseCase: ObserveSessionReportsUseCase,
    observeSessionReportCountUseCase: ObserveSessionReportCountUseCase,
    private val sessionEggCountUseCase: SessionEggCountUseCase,
    private val generateSessionReportUseCase: GenerateSessionReportUseCase,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val generationState = MutableStateFlow(GenerationState())

    /** Zero-based reports page; moved by [goToNextReportPage] / [goToPreviousReportPage]. */
    private val currentReportPage = MutableStateFlow(0)

    // Re-query the DB for just the current page so we never load more than one page of rows.
    private val pagedReports = currentReportPage.flatMapLatest { page ->
        observeSessionReportsUseCase(sessionId, REPORTS_PER_PAGE, page * REPORTS_PER_PAGE)
    }

    private val _events = MutableSharedFlow<SessionDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionDetailEvent> = _events.asSharedFlow()

    val state: StateFlow<SessionDetailState> = combine(
        getSessionSamplesUseCase(sessionId),
        pagedReports,
        observeSessionReportCountUseCase(sessionId),
        generationState,
        currentReportPage,
    ) { session, reports, totalReports, generation, page ->
        val eggCounts = sessionEggCountUseCase(sessionId)
        SessionDetailState(
            session = session,
            eggCounts = eggCounts.counts.map { EggCountSummary(it.species, it.count) },
            totalEggCount = eggCounts.totalEggCount,
            epg = eggCounts.epg,
            infectivityLevel = eggCounts.infectivityLevel,
            infectivitySpeciesLabel = eggCounts.topSpecies?.displayName,
            reports = reports,
            totalReports = totalReports,
            currentPage = page,
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
     * Generates a fresh report for this session in the chosen [format] (the use case writes only
     * that format's file). Emits [SessionDetailEvent.ReportGenerated] on success so the screen can
     * offer the medtech a share action for the format they asked for.
     */
    fun generateReport(format: ExportFormat) {
        viewModelScope.launch {
            generationState.update { it.copy(isGenerating = true, error = null) }
            generateSessionReportUseCase(sessionId, format.toDomain()).fold(
                onSuccess = { report ->
                    generationState.update { GenerationState() }
                    // The new report is newest, so it lands on the first page — jump there.
                    currentReportPage.value = 0
                    _events.emit(
                        SessionDetailEvent.ReportGenerated(
                            pdfPath = report.pdfFilePath,
                            csvPath = report.csvFilePath,
                            format = format,
                        ),
                    )
                },
                onFailure = { error ->
                    generationState.update {
                        GenerationState(error = error.message ?: error::class.simpleName)
                    }
                },
            )
        }
    }

    /** Advances to the next reports page. The UI only enables this when a next page exists. */
    fun goToNextReportPage() {
        currentReportPage.update { it + 1 }
    }

    /** Steps back one reports page, never below the first. */
    fun goToPreviousReportPage() {
        currentReportPage.update { (it - 1).coerceAtLeast(0) }
    }
}

private fun ExportFormat.toDomain(): ReportFormat = when (this) {
    ExportFormat.PDF -> ReportFormat.PDF
    ExportFormat.CSV -> ReportFormat.CSV
}

private data class GenerationState(
    val isGenerating: Boolean = false,
    val error: String? = null,
)
