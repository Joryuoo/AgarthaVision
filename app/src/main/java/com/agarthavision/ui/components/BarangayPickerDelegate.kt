package com.agarthavision.ui.components

import android.util.Log
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the barangay picker is currently showing.
 *
 * [results] is capped by [SearchBarangaysUseCase.RESULT_LIMIT]; there are 42,010 barangays
 * and a short query matches thousands.
 */
data class BarangayPickerState(
    val query: String = "",
    val results: List<PsgcBarangay> = emptyList(),
    val selected: PsgcBarangay? = null,
)

/**
 * The barangay search pipeline and its three callbacks, in one place.
 *
 * Lifted out of `SessionsViewModel` before a second host copied it. The patient form needs
 * exactly the same picker, and two hand-copied debounce implementations diverge into a bug
 * that only ever reports as "search feels different on that screen" — which nobody files.
 *
 * Behaviour is unchanged from the original. The minimum query length and the result cap
 * belong to [SearchBarangaysUseCase]; this wraps them and does not reinterpret them.
 *
 * A host composes one of these and exposes [state], forwarding the three callbacks. It is
 * not a ViewModel and holds no scope of its own: [start] takes the host's
 * `viewModelScope`, so the pipeline dies with the host rather than outliving it.
 */
class BarangayPickerDelegate(
    private val searchBarangaysUseCase: SearchBarangaysUseCase,
) {
    private val internalState = MutableStateFlow(BarangayPickerState())
    val state: StateFlow<BarangayPickerState> = internalState.asStateFlow()

    private val queries = MutableStateFlow("")

    /**
     * Runs the search off the keystroke path, in [scope].
     *
     * Debounced so a medtech typing "cebu" triggers one query instead of four, and
     * [mapLatest] so a slower earlier search cannot land after a newer one and show stale
     * results. The search is a local Room scan — there is no network call here, which is
     * what makes the picker work with the radio off.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            queries
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .mapLatest { query ->
                    searchBarangaysUseCase(query).getOrElse { throwable ->
                        // Without this the picker renders its "no barangay matches" state
                        // for a broken table exactly as it does for a typo, and nothing
                        // anywhere says the dataset failed to seed.
                        Log.w(TAG, "Barangay search failed for query of length ${query.length}.", throwable)
                        emptyList()
                    }
                }
                .collect { results -> internalState.update { it.copy(results = results) } }
        }
    }

    fun onQueryChanged(query: String) {
        internalState.update { it.copy(query = query) }
        queries.value = query
    }

    /**
     * Resolves [code] against the current result set. Ignored when it matches nothing,
     * which can only happen if results changed under a tap already in flight.
     */
    fun onSelected(code: String) {
        val barangay = internalState.value.results.firstOrNull { it.code == code } ?: return
        internalState.update { it.copy(selected = barangay, query = "", results = emptyList()) }
        queries.value = ""
    }

    /** Clears the picker — on the clear button, on dismissal, and after a successful save. */
    fun onCleared() {
        internalState.update { it.copy(selected = null, query = "", results = emptyList()) }
        queries.value = ""
    }

    /** Preselects a stored barangay, for an edit form opening on an existing record. */
    fun preselect(barangay: PsgcBarangay?) {
        internalState.update { it.copy(selected = barangay, query = "", results = emptyList()) }
        queries.value = ""
    }

    private companion object {
        const val TAG = "BarangayPicker"
        const val DEBOUNCE_MS = 150L
    }
}

/**
 * Adapts a barangay to the generic dropdown's option shape.
 *
 * Shared rather than private to one screen: [SearchableDropdown] knows nothing about PSGC,
 * and both the session sheet and the patient form need the same mapping.
 * [PsgcBarangay.parentPath] already yields "City of Cebu · Region VII (Central Visayas)",
 * so the hierarchy is not recomposed at the call site.
 */
fun PsgcBarangay.toOption(): SearchableOption =
    SearchableOption(key = code, title = name, subtitle = parentPath)
