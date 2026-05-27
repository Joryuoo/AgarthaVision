package com.agarthavision.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.GetRecordsUseCase
import com.agarthavision.domain.usecase.records.RecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class RecordsState(
    val records: List<RecordItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSpecies: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val getRecordsUseCase: GetRecordsUseCase,
) : ViewModel() {

    private val _selectedSpecies = MutableStateFlow<EggSpecies?>(null)
    private val _startDate = MutableStateFlow<LocalDate?>(null)
    private val _endDate = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<RecordsState> = combine(
        getRecordsUseCase.invoke(),
        _selectedSpecies,
        _startDate,
        _endDate
    ) { records, species, start, end ->
        val filtered = records.filter { item ->
            val matchesSpecies = species == null || 
                (item.primaryDetection?.expertClass ?: item.primaryDetection?.classLabel)
                    ?.contains(species.name, ignoreCase = true) == true
            
            val recordDate = Instant.ofEpochMilli(item.sample.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            
            val matchesDate = (start == null || !recordDate.isBefore(start)) &&
                              (end == null || !recordDate.isAfter(end))
            
            matchesSpecies && matchesDate
        }
        
        RecordsState(
            records = filtered,
            isLoading = false,
            selectedSpecies = species,
            startDate = start,
            endDate = end
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordsState()
    )

    fun onSpeciesSelected(species: EggSpecies?) {
        _selectedSpecies.value = species
    }

    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        _startDate.value = start
        _endDate.value = end
    }
}
