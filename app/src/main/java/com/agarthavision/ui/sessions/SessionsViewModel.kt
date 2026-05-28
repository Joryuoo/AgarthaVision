package com.agarthavision.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
)

sealed interface SessionsEvent {
    data class NavigateToCapture(val sessionId: String) : SessionsEvent
    data class ShareExport(val content: String) : SessionsEvent
}

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
    private val sessionRemoteDataSource: SessionRemoteDataSource,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionsState())
    private val eventChannel = Channel<SessionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val currentUserId: String? = sessionRemoteDataSource.currentUserId()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionsState> =
        internalState
            .flatMapLatest { snapshot ->
                if (currentUserId == null) {
                    flowOf(snapshot.copy(isLoading = false))
                } else {
                    val since = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS)).toEpochMilli()
                    val sessionsFlow = sessionRepository.observeSessionsWithStats(currentUserId, since)
                    combine(sessionsFlow, internalState) { sessions, latest ->
                        val filtered = if (latest.searchQuery.isBlank()) {
                            sessions
                        } else {
                            sessions.filter { it.session.id.contains(latest.searchQuery, ignoreCase = true) || (it.session.label?.contains(latest.searchQuery, ignoreCase = true) == true) }
                        }
                        latest.copy(sessions = filtered, isLoading = false)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionsState(),
            )

    fun onSearchQueryChanged(query: String) {
        internalState.update { it.copy(searchQuery = query) }
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
                val content = "Export for Session ${session.id} (${session.label ?: "Unnamed"})\nStarted: ${Instant.ofEpochMilli(session.startedAt)}"
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
    }
}
