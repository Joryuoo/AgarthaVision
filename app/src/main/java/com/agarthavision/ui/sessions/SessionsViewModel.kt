package com.agarthavision.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.domain.usecase.sessions.SetSessionClaimExemptUseCase
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
 * `TooManyFunctions` is suppressed for the same reason
 * [com.agarthavision.data.local.dao.SessionDao] suppresses it: one screen's callbacks
 * belong to one ViewModel, and splitting them across two classes to satisfy a count would
 * be inconsistent with every other ViewModel here for no functional benefit.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val setSessionClaimExemptUseCase: SetSessionClaimExemptUseCase,
    private val claimLocalDataUseCase: ClaimLocalDataUseCase,
    private val searchBarangaysUseCase: SearchBarangaysUseCase,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionsState())
    private val eventChannel = Channel<SessionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val barangayQueries = MutableStateFlow("")

    init {
        observeBarangayQueries()
    }

    // Per ADR-007 (hard-rule fix): identity comes from a use case, not a direct
    // data-source read. Null identity (signed-out / offline) still lists local sessions.
    private val userIdFlow = observeLocalIdentityUseCase().map { it?.userId }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionsState> =
        userIdFlow
            .flatMapLatest { userId ->
                val sessionsFlow = if (userId == null) {
                    sessionRepository.observeVisibleSessions(null)
                        .map { sessions -> sessions.map { SessionWithStats(it, 0, 0, 0, 0) } }
                } else {
                    val since = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS)).toEpochMilli()
                    sessionRepository.observeSessionsWithStats(userId, since)
                }
                combine(sessionsFlow, internalState) { sessions, latest ->
                    val filtered = if (latest.searchQuery.isBlank()) {
                        sessions
                    } else {
                        sessions.filter {
                            it.session.id.contains(latest.searchQuery, ignoreCase = true) ||
                                (it.session.label?.contains(latest.searchQuery, ignoreCase = true) == true)
                        }
                    }
                    latest.copy(sessions = filtered, isLoading = false)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionsState(),
            )

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

    /**
     * Runs the barangay search off the keystroke path.
     *
     * Debounced so a medtech typing "cebu" triggers one query instead of four, and
     * [mapLatest] so a slower earlier search cannot land after a newer one and show stale
     * results. The search itself is a local Room scan — there is no network call here, which
     * is what makes the picker work with the radio off.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeBarangayQueries() {
        viewModelScope.launch {
            barangayQueries
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .mapLatest { query -> searchBarangaysUseCase(query).getOrDefault(emptyList()) }
                .collect { results -> internalState.update { it.copy(barangayResults = results) } }
        }
    }

    fun onBarangayQueryChanged(query: String) {
        internalState.update { it.copy(barangayQuery = query) }
        barangayQueries.value = query
    }

    /**
     * Resolves [code] against the current result set. Ignored when it matches nothing,
     * which can only happen if results changed under a tap already in flight.
     */
    fun onBarangaySelected(code: String) {
        val barangay = internalState.value.barangayResults.firstOrNull { it.code == code } ?: return
        internalState.update {
            it.copy(selectedBarangay = barangay, barangayQuery = "", barangayResults = emptyList())
        }
        barangayQueries.value = ""
    }

    /** Clears the picker — on the clear button, on sheet dismissal, and after a session starts. */
    fun onBarangayCleared() {
        internalState.update {
            it.copy(selectedBarangay = null, barangayQuery = "", barangayResults = emptyList())
        }
        barangayQueries.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        internalState.update { it.copy(searchQuery = query) }
    }

    fun onCreateSession(label: String, notes: String?) {
        if (label.isBlank()) {
            internalState.update { it.copy(errorMessage = "Label is required.") }
            return
        }
        // The barangay is what makes a smear mappable, so a new session has to carry one.
        // Sessions predating the picker keep a null code; nothing backfills them.
        val barangay = internalState.value.selectedBarangay
        if (barangay == null) {
            internalState.update { it.copy(errorMessage = "Barangay is required.") }
            return
        }
        if (internalState.value.isCreating) return
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
                onBarangayCleared()
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

        /** Long enough to coalesce a burst of keystrokes, short enough to feel immediate. */
        private const val SEARCH_DEBOUNCE_MS = 150L
    }
}
