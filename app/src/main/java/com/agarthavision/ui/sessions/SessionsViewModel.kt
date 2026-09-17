package com.agarthavision.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.sanitizeDateRange
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.BarangayPickerDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsState(
    val sessions: List<SessionWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val totalCount: Int = 0,
    /** Frames awaiting review across the filtered sessions. See [SessionsCounts]. */
    val unverifiedCount: Int = 0,
    val canLoadMore: Boolean = false,
    /** Current text in the New Session sheet's barangay picker. */
    val barangayQuery: String = "",
    /** Matches for [barangayQuery], capped by [SearchBarangaysUseCase.RESULT_LIMIT]. */
    val barangayResults: List<PsgcBarangay> = emptyList(),
    /** The barangay chosen for the session being created. Required before it can start. */
    val selectedBarangay: PsgcBarangay? = null,
)

sealed interface SessionsEvent {
    data class NavigateToCapture(val sessionId: String) : SessionsEvent
    data class ShareExport(val content: String) : SessionsEvent
}

/**
 * Backs the Sessions list and the New Session sheet.
 *
 * The list pipeline mirrors [com.agarthavision.ui.records.RecordsViewModel]:
 * `combine(inputs) → flatMapLatest → combine(page, counts, internal, rawSearch)`, with
 * SQL-backed filtering, pagination, and search. Uses [SharingStarted.WhileSubscribed] so
 * the upstream Room query is cancelled when the screen leaves composition while the
 * replay cache retains the last value. Per ADR-007 (identity dispatch).
 *
 * `TooManyFunctions` is suppressed for the same reason
 * [com.agarthavision.data.local.dao.SessionDao] suppresses it: one screen's callbacks
 * belong to one ViewModel, and splitting them across two classes to satisfy a count would
 * be inconsistent with every other ViewModel here for no functional benefit.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val searchBarangaysUseCase: SearchBarangaysUseCase,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionsState())
    private val eventChannel = Channel<SessionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /**
     * The barangay picker, shared with the patient form (PB-07c) rather than copied into
     * it. This ViewModel's own surface is unchanged — [SessionsState] still carries the
     * three flat fields and the three callbacks below still exist — so
     * `SessionPickerViewModelTest` remains the regression net for behaviour that did not
     * change.
     */
    private val barangayPicker = BarangayPickerDelegate(searchBarangaysUseCase)

    init {
        barangayPicker.start(viewModelScope)
        mirrorBarangayPickerState()
    }

    // Per ADR-007 (hard-rule fix): identity comes from a use case, not a direct
    // data-source read. Null identity (signed-out / offline) still lists local sessions.
    private val userIdFlow = observeLocalIdentityUseCase().map { it?.userId }

    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)
    private val searchQuery = MutableStateFlow("")
    private val limit = MutableStateFlow(INITIAL_PAGE)

    // Debounced search prevents a new Room query on every keystroke; raw searchQuery
    // is still combined into the final state so the text field reflects input immediately.
    private val debouncedSearch = searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    /** Bundled upstream inputs, re-emitted whenever any input changes. */
    private data class SessionsInputs(
        val userId: String?,
        val activeSessionId: String?,
        val start: LocalDate?,
        val end: LocalDate?,
        val debouncedQuery: String,
        val limit: Int,
    )

    /**
     * The session the medtech is working in, or null. Exempt from the date filter so the
     * open smear is never filtered out of the list it is reached from.
     */
    private val activeSessionIdFlow = sessionManager.state
        .map { (it as? SessionState.Active)?.session?.sessionId }
        .distinctUntilChanged()

    private val queryInputs = combine(
        combine(userIdFlow, activeSessionIdFlow) { uid, activeId -> uid to activeId },
        startDate, endDate, debouncedSearch, limit,
    ) { (uid, activeId), st, en, q, lim -> SessionsInputs(uid, activeId, st, en, q, lim) }

    /**
     * Observable UI state for the Sessions screen.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second stop timeout so the upstream
     * Room query is cancelled when there are no active collectors (e.g. the screen leaves
     * composition), but the [StateFlow]'s replay cache retains the last emitted value.
     * A fresh collector therefore receives the last non-loading state immediately — no
     * flicker back to the loading skeleton on resubscribe — while the query eventually
     * restarts and emits a fresh update.
     *
     * [searchQuery] is combined from the raw (un-debounced) flow so the text field
     * reflects every keystroke immediately, while [sessions] and [totalCount]/[unverifiedCount]
     * only update after the debounce window.
     */
    val state: StateFlow<SessionsState> = queryInputs
        .flatMapLatest { inputs ->
            val zone = ZoneId.systemDefault()
            val sinceMillis = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS)).toEpochMilli()
            val startMillis = inputs.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            val endMillis = inputs.end
                ?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()

            // Escape the free-text needle so `%`, `_`, and `\` in user input are literal.
            // Mirrors the escaping in GetRecordsUseCase so SQL behaviour is consistent.
            val escaped = inputs.debouncedQuery
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            combine(
                sessionRepository.observeVisibleSessionsPage(
                    inputs.userId, inputs.activeSessionId, sinceMillis, startMillis, endMillis,
                    escaped, inputs.limit,
                ),
                sessionRepository.observeVisibleSessionsCounts(
                    inputs.userId, inputs.activeSessionId, sinceMillis, startMillis, endMillis,
                    escaped,
                ),
                internalState,
                searchQuery,
            ) { sessions, counts, internal, rawSearch ->
                internal.copy(
                    sessions = sessions,
                    isLoading = false,
                    startDate = inputs.start,
                    endDate = inputs.end,
                    searchQuery = rawSearch,
                    totalCount = counts.totalCount,
                    unverifiedCount = counts.unverifiedCount,
                    canLoadMore = sessions.size >= inputs.limit,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionsState(),
        )

    /**
     * Updates the free-text search query. Resets pagination.
     */
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        limit.value = INITIAL_PAGE
    }

    /**
     * Applies an inclusive session-start date range. Resets pagination.
     */
    fun onDateRangeSelected(start: LocalDate?, end: LocalDate?) {
        // Sessions cannot have started in the future; clamp before the range hits SQL.
        val (safeStart, safeEnd) = sanitizeDateRange(start, end)
        startDate.value = safeStart
        endDate.value = safeEnd
        limit.value = INITIAL_PAGE
    }

    /**
     * Requests the next page of results.
     */
    fun onLoadMore() {
        limit.value += PAGE_STEP
    }

    /**
     * Keeps [SessionsState]'s barangay fields in step with the delegate.
     *
     * The delegate owns the pipeline; the flat fields stay on the state because the sheet
     * and its tests already read them, and changing that shape would be a second,
     * unrelated change riding on a behaviour-preserving refactor.
     */
    private fun mirrorBarangayPickerState() {
        viewModelScope.launch {
            barangayPicker.state.collect { picker ->
                internalState.update {
                    it.copy(
                        barangayQuery = picker.query,
                        barangayResults = picker.results,
                        selectedBarangay = picker.selected,
                    )
                }
            }
        }
    }

    fun onBarangayQueryChanged(query: String) = barangayPicker.onQueryChanged(query)

    fun onBarangaySelected(code: String) = barangayPicker.onSelected(code)

    /** Clears the picker — on the clear button, on sheet dismissal, and after a session starts. */
    fun onBarangayCleared() = barangayPicker.onCleared()

    fun onCreateSession(label: String, notes: String?) {
        if (internalState.value.isCreating) return
        // The barangay is what makes a smear mappable, so a new session has to carry one.
        // Sessions predating the picker keep a null code; nothing backfills them.
        val barangay = internalState.value.selectedBarangay
        if (label.isBlank() || barangay == null) {
            // Both guards are defence in depth — SessionsScreen blocks submit before it gets
            // here. The barangay wording is kept identical to `session_new_barangay_required`
            // so the two paths cannot drift into two different messages for one rule.
            val reason = if (label.isBlank()) LABEL_REQUIRED else BARANGAY_REQUIRED
            internalState.update { it.copy(errorMessage = reason) }
            return
        }
        internalState.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                sessionManager.startSession(
                    label = label.trim(),
                    psgcBarangayCode = barangay.code,
                    notes = notes?.takeIf { it.isNotBlank() },
                )
            }.onSuccess { entity ->
                internalState.update { it.copy(isCreating = false, errorMessage = null) }
                barangayPicker.onCleared()
                eventChannel.send(SessionsEvent.NavigateToCapture(entity.sessionId))
            }.onFailure { error ->
                // The sheet has already closed and its label/note state has gone with it, so
                // leaving the selection behind would reopen a half-filled sheet.
                barangayPicker.onCleared()
                internalState.update {
                    it.copy(isCreating = false, errorMessage = error.message ?: "Failed to create session.")
                }
            }
        }
    }

    fun onResumeSession(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionManager.resumeSession(sessionId) }
                .onSuccess { eventChannel.send(SessionsEvent.NavigateToCapture(sessionId)) }
                .onFailure { error ->
                    internalState.update {
                        it.copy(errorMessage = error.message ?: "Could not open session.")
                    }
                }
        }
    }

    fun onRenameSession(sessionId: String, newLabel: String) {
        if (newLabel.isBlank()) return
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSessionLabel(sessionId, newLabel.trim())
            }.onFailure { error ->
                internalState.update {
                    it.copy(errorMessage = error.message ?: "Could not rename session.")
                }
            }
        }
    }

    fun onExportSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                val session = sessionRepository.getSessionById(sessionId) ?: return@runCatching
                // In a real app, this would query samples and detections and generate a CSV.
                // For now, we generate a basic summary text to share.
                val content = "Export for Session ${session.id} (${session.label ?: "Unnamed"})\n" +
                    "Started: ${Instant.ofEpochMilli(session.startedAt)}"
                eventChannel.send(SessionsEvent.ShareExport(content))
            }.onFailure { error ->
                internalState.update {
                    it.copy(errorMessage = error.message ?: "Could not export session.")
                }
            }
        }
    }

    fun onDismissError() {
        internalState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        private const val RECENT_WINDOW_DAYS = 30L
        private const val INITIAL_PAGE = 5
        private const val PAGE_STEP = 10

        /** Debounce for the session list's free-text search before it hits Room. */
        private const val SEARCH_DEBOUNCE_MS = 300L

        // Copy lives here rather than in strings.xml to match the other ViewModels in this
        // module (see CaptureViewModel). Lifting all of it into resources needs an error-type
        // seam across every screen state, which is a wider change than this ticket.
        private const val LABEL_REQUIRED = "Label is required."

        /** Must stay word-for-word identical to `R.string.session_new_barangay_required`. */
        private const val BARANGAY_REQUIRED = "Please select the patient's barangay to continue."
    }
}
