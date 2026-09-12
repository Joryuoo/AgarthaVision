package com.agarthavision.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SetSessionClaimExemptUseCase
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import com.agarthavision.core.util.sanitizeDateRange
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
import kotlinx.coroutines.flow.first
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
    val activeCount: Int = 0,
    val canLoadMore: Boolean = false,
)

sealed interface SessionsEvent {
    data class NavigateToCapture(val sessionId: String) : SessionsEvent
    data class ShareExport(val content: String) : SessionsEvent
}

/**
 * Drives the Sessions screen with SQL-backed filtering, pagination, and search.
 *
 * The pipeline mirrors [com.agarthavision.ui.records.RecordsViewModel]:
 * `combine(inputs) → flatMapLatest → combine(page, counts, internal, rawSearch)`.
 * Uses [SharingStarted.WhileSubscribed] so the upstream Room query is cancelled
 * when the screen leaves composition while the replay cache retains the last value.
 * Per ADR-007 (identity dispatch).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val setSessionClaimExemptUseCase: SetSessionClaimExemptUseCase,
    private val claimLocalDataUseCase: ClaimLocalDataUseCase,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionsState())
    private val eventChannel = Channel<SessionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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
        val start: LocalDate?,
        val end: LocalDate?,
        val debouncedQuery: String,
        val limit: Int,
    )

    private val queryInputs = combine(
        userIdFlow, startDate, endDate, debouncedSearch, limit,
    ) { uid, st, en, q, lim -> SessionsInputs(uid, st, en, q, lim) }

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
     * reflects every keystroke immediately, while [sessions] and [totalCount]/[activeCount]
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
                    inputs.userId, sinceMillis, startMillis, endMillis, escaped, inputs.limit,
                ),
                sessionRepository.observeVisibleSessionsCounts(
                    inputs.userId, sinceMillis, startMillis, endMillis, escaped,
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
                    activeCount = counts.activeCount,
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
     * Toggles a session's account link (per ADR-007). When the session is unowned it
     * flips the claim-exempt opt-out; when it is owned + pending the user can unlink it;
     * an unowned session with an available identity can be claimed on demand.
     */
    fun onToggleAccountLink(sessionId: String, link: Boolean) {
        viewModelScope.launch {
            val userId = userIdFlow.first()
            val result = if (link && userId != null) {
                claimLocalDataUseCase(userId, sessionIds = listOf(sessionId))
                Result.success(Unit)
            } else {
                // link == false → opt out of claiming (or unlink a still-pending session).
                setSessionClaimExemptUseCase(sessionId, exempt = !link)
            }
            result.onFailure { error ->
                internalState.update { it.copy(errorMessage = error.message ?: "Could not update link.") }
            }
        }
    }

    fun onCreateSession(label: String, notes: String?) {
        if (label.isBlank()) {
            internalState.update { it.copy(errorMessage = "Label is required.") }
            return
        }
        if (internalState.value.isCreating) return
        internalState.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                sessionManager.startSession(label = label.trim(), notes = notes?.takeIf { it.isNotBlank() })
            }.onSuccess { entity ->
                internalState.update { it.copy(isCreating = false, errorMessage = null) }
                eventChannel.send(SessionsEvent.NavigateToCapture(entity.sessionId))
            }.onFailure { error ->
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

    fun onEndSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                val entity = sessionRepository.getSessionById(sessionId) ?: return@runCatching
                if (entity.endedAt != null) return@runCatching
                sessionManager.resumeSession(sessionId)
                sessionManager.stopSession()
            }.onFailure { error ->
                internalState.update {
                    it.copy(errorMessage = error.message ?: "Could not end session.")
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
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
