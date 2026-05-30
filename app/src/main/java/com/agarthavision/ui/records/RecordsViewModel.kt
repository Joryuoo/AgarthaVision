package com.agarthavision.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.SessionRecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * UI state for the session-first records browser.
 */
data class RecordsState(
    val sessions: List<SessionRecordItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSpecies: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

/**
 * Filters session records by species and date range.
 */
@HiltViewModel
class RecordsViewModel @Inject constructor(
    getRecordsUseCase: GetRecordsUseCase,
) : ViewModel() {

    private val selectedSpecies = MutableStateFlow<EggSpecies?>(null)
    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<RecordsState> = combine(
        getRecordsUseCase.invoke(),
        selectedSpecies,
        startDate,
        endDate,
    ) { sessions, species, start, end ->
        RecordsState(
            sessions = sessions.filter { item ->
                item.matchesSpecies(species) && item.matchesDateRange(start, end)
            },
            isLoading = false,
            selectedSpecies = species,
            startDate = start,
            endDate = end,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsState(),
    )

    /**
     * Selects a species filter, or clears it when null.
     */
    fun onSpeciesSelected(species: EggSpecies?) {
        selectedSpecies.value = species
    }

    /**
     * Applies an inclusive session-start date range.
     */
    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        startDate.value = start
        endDate.value = end
    }

    private fun SessionRecordItem.matchesSpecies(species: EggSpecies?): Boolean {
        val needle = species?.canonicalClass ?: return true
        return speciesLabels.any { label -> label.contains(needle, ignoreCase = true) }
    }

    private fun SessionRecordItem.matchesDateRange(start: LocalDate?, end: LocalDate?): Boolean {
        val sessionDate = Instant.ofEpochMilli(session.startedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return (start == null || !sessionDate.isBefore(start)) &&
            (end == null || !sessionDate.isAfter(end))
    }
}
