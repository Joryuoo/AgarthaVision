package com.agarthavision.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.util.sanitizeDateRange
import com.agarthavision.data.supabase.RestoreReportFilesUseCase
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.usecase.records.ObserveReportsUseCase
import com.agarthavision.domain.usecase.records.ReportsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the cross-patient reports browser.
 */
data class RecordsState(
    val reports: List<Report> = emptyList(),
    val totalReports: Int = 0,
    val unsyncedReports: Int = 0,
    val isLoading: Boolean = true,
    val selectedSpecies: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val canLoadMore: Boolean = false,
    /**
     * One instant per emission, sourced from the injected [Clock], so the screen's
     * Today/This-week bucketing always reflects the same "now" the state was computed with —
     * rather than a Composable-local `System.currentTimeMillis()` snapshot frozen at first
     * composition.
     */
    val now: Instant = Instant.now(),
)

/**
 * One-shot UI events emitted by the Records/Reports screen while restoring a report's PDF from
 * Storage. Mirrors `SessionDetailEvent`'s restore trio — same reason, same messaging.
 */
sealed interface RecordsEvent {
    /** A report's file was not on this device and is being fetched from Storage. */
    data object ReportRestoreStarted : RecordsEvent

    /** A report's PDF is now on this device, at this path. Null only for a legacy CSV-only report. */
    data class ReportRestored(val pdfPath: String?) : RecordsEvent

    /** Nothing could be recovered: the report predates the `reports` bucket, or the device is offline. */
    data object ReportRestoreFailed : RecordsEvent
}

/**
 * Drives the Reports/Records screen with SQL-backed filtering, pagination, and search.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    observeReportsUseCase: ObserveReportsUseCase,
    private val restoreReportFilesUseCase: RestoreReportFilesUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val selectedSpecies = MutableStateFlow<EggSpecies?>(null)
    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)
    private val searchQuery = MutableStateFlow("")
    private val limit = MutableStateFlow(PAGE_SIZE)

    /**
     * Reports with a restore in flight. Only touched from the main thread — the tap and
     * [viewModelScope]'s dispatcher — so a plain set is enough. See
     * [SessionDetailViewModel.restoringReportIds] for why this guard exists.
     */
    private val restoringReportIds = mutableSetOf<String>()

    private val _events = MutableSharedFlow<RecordsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RecordsEvent> = _events.asSharedFlow()

    // Debounced search prevents a new Room query on every keystroke; raw searchQuery
    // is still combined into the final state so the text field reflects input immediately.
    private val debouncedSearch = searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    // Upstream pipeline: query params (debounced search) → use-case → (ReportsQuery, ReportsResult)
    private val resultFlow = combine(
        selectedSpecies, startDate, endDate, debouncedSearch, limit,
    ) { sp, st, en, q, lim ->
        ReportsQuery(species = sp, startDate = st, endDate = en, searchQuery = q, limit = lim)
    }.flatMapLatest { q -> observeReportsUseCase(q).map { q to it } }

    /**
     * Observable UI state for the Records/Reports screen.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second stop timeout so the upstream
     * Room query is cancelled when there are no active collectors (e.g. the screen leaves
     * composition), but the [StateFlow]'s replay cache retains the last emitted value.
     * A fresh collector therefore receives the last non-loading state immediately — no
     * flicker back to the loading skeleton on resubscribe — while the query eventually
     * restarts and emits a fresh update.
     *
     * [searchQuery] is combined from the raw (un-debounced) flow so the text field
     * reflects every keystroke immediately, while [reports] and [totalReports] only update
     * after the debounce window.
     */
    val state: StateFlow<RecordsState> = combine(resultFlow, searchQuery) { (q, result), rawSearch ->
        RecordsState(
            reports = result.items,
            totalReports = result.totalCount,
            unsyncedReports = result.unsyncedCount,
            isLoading = false,
            selectedSpecies = q.species,
            startDate = q.startDate,
            endDate = q.endDate,
            searchQuery = rawSearch,
            canLoadMore = result.items.size < result.totalCount,
            now = clock.instant(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsState(),
    )

    /**
     * Selects a species filter, or clears it when null. Resets pagination.
     */
    fun onSpeciesSelected(species: EggSpecies?) {
        selectedSpecies.value = species
        limit.value = PAGE_SIZE
    }

    /**
     * Applies an inclusive date range. Resets pagination.
     */
    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        val (safeStart, safeEnd) = sanitizeDateRange(start, end)
        startDate.value = safeStart
        endDate.value = safeEnd
        limit.value = PAGE_SIZE
    }

    /**
     * Updates the free-text search query. Resets pagination.
     */
    fun onSearchChanged(query: String) {
        searchQuery.value = query
        limit.value = PAGE_SIZE
    }

    /**
     * Requests the next page of results.
     */
    fun onLoadMore() {
        limit.value += PAGE_SIZE
    }

    /**
     * Fetches a report's PDF from Storage when this device does not have it — the same restore
     * [SessionDetailViewModel] runs, needed here too since a patient report card is reachable
     * (and openable) straight from this tab, on a device that never generated it.
     */
    fun restoreReportFiles(reportId: String) {
        // A second tap while the first download is still running would fetch the same object
        // again and write a second copy beside the first — and nothing deletes the extra (C8).
        if (!restoringReportIds.add(reportId)) return
        viewModelScope.launch {
            try {
                _events.emit(RecordsEvent.ReportRestoreStarted)
                restoreReportFilesUseCase(reportId).fold(
                    onSuccess = { files -> _events.emit(RecordsEvent.ReportRestored(pdfPath = files.pdfFilePath)) },
                    onFailure = { _events.emit(RecordsEvent.ReportRestoreFailed) },
                )
            } finally {
                restoringReportIds.remove(reportId)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
