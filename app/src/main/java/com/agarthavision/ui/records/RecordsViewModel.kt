package com.agarthavision.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.RecordsQuery
import com.agarthavision.domain.usecase.records.SessionRecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * UI state for the session-first records browser.
 */
data class RecordsState(
    val sessions: List<SessionRecordItem> = emptyList(),
    val totals: RecordsTotals = RecordsTotals(),
    val isLoading: Boolean = true,
    val selectedSpecies: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val canLoadMore: Boolean = false,
)

/**
 * Drives the Records screen with SQL-backed filtering, pagination, and search.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    getRecordsUseCase: GetRecordsUseCase,
) : ViewModel() {

    private val selectedSpecies = MutableStateFlow<EggSpecies?>(null)
    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)
    private val searchQuery = MutableStateFlow("")
    private val limit = MutableStateFlow(PAGE_SIZE)

    // Debounced search prevents a new Room query on every keystroke; raw searchQuery
    // is still combined into the final state so the text field reflects input immediately.
    private val debouncedSearch = searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    // Upstream pipeline: query params (debounced search) → use-case → (RecordsQuery, RecordsResult)
    private val resultFlow = combine(
        selectedSpecies, startDate, endDate, debouncedSearch, limit,
    ) { sp, st, en, q, lim ->
        RecordsQuery(species = sp, startDate = st, endDate = en, searchQuery = q, limit = lim)
    }.flatMapLatest { q -> getRecordsUseCase(q).map { q to it } }

    /**
     * Observable UI state for the Records screen.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second stop timeout so the upstream
     * Room query is cancelled when there are no active collectors (e.g. the screen leaves
     * composition), but the [StateFlow]'s replay cache retains the last emitted value.
     * A fresh collector therefore receives the last non-loading state immediately — no
     * flicker back to the loading skeleton on resubscribe — while the query eventually
     * restarts and emits a fresh update.
     *
     * [searchQuery] is combined from the raw (un-debounced) flow so the text field
     * reflects every keystroke immediately, while [sessions] and [totals] only update
     * after the debounce window.
     */
    val state: StateFlow<RecordsState> = combine(resultFlow, searchQuery) { (q, result), rawSearch ->
        RecordsState(
            sessions = result.items,
            totals = result.totals,
            isLoading = false,
            selectedSpecies = q.species,
            startDate = q.startDate,
            endDate = q.endDate,
            searchQuery = rawSearch,
            canLoadMore = result.items.size >= q.limit,
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
     * Applies an inclusive session-start date range. Resets pagination.
     */
    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        startDate.value = start
        endDate.value = end
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

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
