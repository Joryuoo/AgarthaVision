package com.agarthavision.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.RecordsQuery
import com.agarthavision.domain.usecase.records.SessionRecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * UI state for the session-first records browser.
 */
data class RecordsState(
    val sessions: List<SessionRecordItem> = emptyList(),
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
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    getRecordsUseCase: GetRecordsUseCase,
) : ViewModel() {

    private val selectedSpecies = MutableStateFlow<EggSpecies?>(null)
    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)
    private val searchQuery = MutableStateFlow("")
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val retained = MutableStateFlow(RecordsState())
    val state: StateFlow<RecordsState> = retained.asStateFlow()

    init {
        combine(selectedSpecies, startDate, endDate, searchQuery, limit) { sp, st, en, q, lim ->
            RecordsQuery(species = sp, startDate = st, endDate = en, searchQuery = q, limit = lim)
        }.flatMapLatest { q -> getRecordsUseCase(q).map { q to it } }
            .onEach { (q, items) ->
                retained.value = RecordsState(
                    sessions = items,
                    isLoading = false,
                    selectedSpecies = q.species,
                    startDate = q.startDate,
                    endDate = q.endDate,
                    searchQuery = q.searchQuery,
                    canLoadMore = items.size >= q.limit,
                )
            }.launchIn(viewModelScope)
    }

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
    }
}
