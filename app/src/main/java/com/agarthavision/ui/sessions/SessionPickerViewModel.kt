package com.agarthavision.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the session picker.
 */
data class SessionPickerState(
    val sessions: List<SessionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * One-shot navigation/UX events emitted by the picker.
 */
sealed interface SessionPickerEvent {
    data class NavigateToCapture(val sessionId: String) : SessionPickerEvent
}

/**
 * Backs [SessionPickerScreen]. Lists the user's active + last 30 days of sessions and
 * mediates create/resume/end actions through [SessionManager]. Per ADR-005 and
 * docs/03_MOBILE_APP_PLAN.md §1.1.
 */
@HiltViewModel
class SessionPickerViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val sessionManager: SessionManager,
    private val sessionRemoteDataSource: SessionRemoteDataSource,
) : ViewModel() {

    private val internalState = MutableStateFlow(SessionPickerState())
    private val eventChannel = Channel<SessionPickerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val currentUserId: String? = sessionRemoteDataSource.currentUserId()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionPickerState> =
        internalState
            .flatMapLatest { snapshot ->
                if (currentUserId == null) {
                    flowOf(snapshot.copy(isLoading = false))
                } else {
                    val since = Instant.now().minus(Duration.ofDays(RECENT_WINDOW_DAYS)).toEpochMilli()
                    val sessionsFlow = sessionDao.observeActiveAndRecent(currentUserId, since)
                    kotlinx.coroutines.flow.combine(sessionsFlow, internalState) { sessions, latest ->
                        latest.copy(sessions = sessions, isLoading = false)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionPickerState(),
            )

    /**
     * Creates a new smear session with [label] (required) and optional [notes],
     * then emits a navigate-to-Capture event.
     */
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
                eventChannel.send(SessionPickerEvent.NavigateToCapture(entity.sessionId))
            }.onFailure { error ->
                internalState.update {
                    it.copy(isCreating = false, errorMessage = error.message ?: "Failed to create session.")
                }
            }
        }
    }

    /**
     * Resumes an active session (`endedAt == null`) and navigates to Capture.
     */
    fun onResumeSession(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionManager.resumeSession(sessionId) }
                .onSuccess { eventChannel.send(SessionPickerEvent.NavigateToCapture(sessionId)) }
                .onFailure { error ->
                    internalState.update {
                        it.copy(errorMessage = error.message ?: "Could not open session.")
                    }
                }
        }
    }

    /**
     * Ends an active session from the picker (kebab menu). Does not navigate.
     */
    fun onEndSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                val entity = sessionDao.getSessionById(sessionId) ?: return@runCatching
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

    fun onDismissError() {
        internalState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        private const val RECENT_WINDOW_DAYS = 30L
    }
}
