package com.agarthavision.ui.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.patients.ObservePatientsUseCase
import com.agarthavision.domain.usecase.patients.PatientListItem
import com.agarthavision.domain.usecase.patients.PatientSort
import com.agarthavision.domain.usecase.patients.PatientsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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

/** UI state for the patient list. */
data class PatientsState(
    val patients: List<PatientListItem> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sort: PatientSort = PatientSort.RECENT,
    val canLoadMore: Boolean = false,
    /**
     * One instant per emission, so every row in a given render computes its age against
     * the same clock reading. Reading the clock per row would let a list drawn across
     * midnight show two ages for the same birthday.
     */
    val now: Instant = Instant.now(),
)

/** One-shot navigation out of the list. */
sealed interface PatientsEvent {
    /** Open this patient's session list. */
    data class OpenPatient(val patientId: String) : PatientsEvent

    /** Open the form on an existing patient. */
    data class EditPatient(val patientId: String) : PatientsEvent

    /** Open the blank New Patient form. */
    data object CreatePatient : PatientsEvent
}

/**
 * Drives the patient list: SQL-backed filtering, debounced search, load-more pagination.
 *
 * The pipeline is the one already proven in
 * [com.agarthavision.ui.records.RecordsViewModel]: `combine(inputs)` → [flatMapLatest] →
 * `combine(page, rawSearch)`, with [debounce] on the query and
 * [SharingStarted.WhileSubscribed] on the result.
 *
 * That last part matters for two separate reasons, and neither is obvious: the Room query
 * is cancelled when the screen leaves composition, **and** the replay cache hands a
 * returning collector the last value immediately, so switching tabs and coming back does
 * not flicker through the skeleton.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PatientsViewModel @Inject constructor(
    observePatientsUseCase: ObservePatientsUseCase,
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sort = MutableStateFlow(PatientSort.RECENT)
    private val limit = MutableStateFlow(PatientsQuery.PAGE_SIZE)

    private val eventFlow = MutableSharedFlow<PatientsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PatientsEvent> = eventFlow.asSharedFlow()

    // Identity comes from a use case, not a direct data-source read (ADR-007).
    private val userIdFlow = observeLocalIdentityUseCase().map { it?.userId }

    // Debounced so a medtech typing a surname triggers one Room query rather than one per
    // keystroke; the raw flow is combined back in below so the field stays responsive.
    private val debouncedSearch = searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    private val resultFlow = combine(userIdFlow, debouncedSearch, sort, limit) { userId, query, s, lim ->
        userId to PatientsQuery(query = query, sort = s, limit = lim)
    }.flatMapLatest { (userId, query) ->
        observePatientsUseCase(userId, query).map { query to it }
    }

    val state: StateFlow<PatientsState> =
        combine(resultFlow, searchQuery) { (query, result), rawSearch ->
            PatientsState(
                patients = result.items,
                total = result.total,
                isLoading = false,
                searchQuery = rawSearch,
                sort = query.sort,
                canLoadMore = result.items.size < result.total,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PatientsState(),
        )

    /** Resets pagination: a new filter should not start halfway down the previous one. */
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        limit.value = PatientsQuery.PAGE_SIZE
    }

    fun onSortSelected(sort: PatientSort) {
        this.sort.value = sort
        limit.value = PatientsQuery.PAGE_SIZE
    }

    fun onLoadMore() {
        limit.value += PatientsQuery.PAGE_SIZE
    }

    fun onPatientSelected(patientId: String) {
        viewModelScope.launch { eventFlow.emit(PatientsEvent.OpenPatient(patientId)) }
    }

    /**
     * Opens the form on an existing patient.
     *
     * Separate from [onPatientSelected] because a row tap goes to that patient's smears, which
     * is what a medtech wants nearly every time. Editing is the rarer, deliberate action, so
     * it gets its own affordance rather than displacing the common one.
     */
    fun onEditPatient(patientId: String) {
        viewModelScope.launch { eventFlow.emit(PatientsEvent.EditPatient(patientId)) }
    }

    fun onCreatePatient() {
        viewModelScope.launch { eventFlow.emit(PatientsEvent.CreatePatient) }
    }

    private companion object {
        /** Matches the Records and Sessions lists so search feels the same everywhere. */
        const val SEARCH_DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
