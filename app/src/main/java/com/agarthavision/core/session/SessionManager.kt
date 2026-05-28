package com.agarthavision.core.session

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped tracker of the active capture session.
 *
 * Per ADR-005, a session = one fecal smear. The SessionPicker drives creation
 * via [startSession] with a required `label` (smear name) and optional `notes`
 * (in-session observations). [resumeSession] is used when the medtech reopens
 * an existing active session from the picker. [stopSession] sets `ended_at`
 * locally and remotely.
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.1.
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val remoteDataSource: SessionRemoteDataSource,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)

    /**
     * Current app-level capture session state.
     */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Starts a capture session for the currently authenticated Supabase user.
     *
     * @param label The fecal-smear name the medtech entered in the picker.
     * @param notes Optional in-session observations (slide condition, prep quality, etc.).
     * @return The locally persisted session row mirrored to Supabase.
     * @throws IllegalStateException when no Supabase user session is available.
     */
    suspend fun startSession(label: String, notes: String? = null): SessionEntity {
        val now = Instant.now()
        val userId = remoteDataSource.currentUserId()
            ?: error("A Supabase user session is required to start a recording session.")
        val entity = SessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            deviceId = deviceIdProvider.id,
            startedAt = now.toEpochMilli(),
            endedAt = null,
            notes = notes,
            label = label,
        )
        sessionDao.insertSession(entity)
        runCatching {
            remoteDataSource.insertSession(entity)
        }.onFailure {
            sessionDao.updateSession(entity.copy(endedAt = Instant.now().toEpochMilli()))
            throw it
        }
        _state.value = SessionState.Active(entity, now, isInferenceRunning = true)
        return entity
    }

    /**
     * Transitions to an existing active session (e.g. when the medtech taps a row
     * in the picker for a still-open smear). Does not touch Room or Supabase.
     */
    suspend fun resumeSession(sessionId: String): SessionEntity {
        val entity = sessionDao.getSessionById(sessionId)
            ?: error("Session $sessionId not found locally.")
        check(entity.endedAt == null) { "Cannot resume an already-ended session." }
        _state.value = SessionState.Active(
            session = entity,
            startedAt = Instant.ofEpochMilli(entity.startedAt),
            isInferenceRunning = true,
        )
        return entity
    }

    /**
     * Flips [SessionState.Active.isInferenceRunning] to true. No-op when [SessionState.Idle].
     */
    fun resumeInference() {
        val current = _state.value
        if (current is SessionState.Active && !current.isInferenceRunning) {
            _state.value = current.copy(isInferenceRunning = true)
        }
    }

    /**
     * Flips [SessionState.Active.isInferenceRunning] to false. No-op when [SessionState.Idle].
     */
    fun pauseInference() {
        val current = _state.value
        if (current is SessionState.Active && current.isInferenceRunning) {
            _state.value = current.copy(isInferenceRunning = false)
        }
    }

    /**
     * Ends the active session, if one exists. Optional [notes] override lets the
     * End-Session confirmation dialog save final observations.
     */
    suspend fun stopSession(notes: String? = null) {
        val current = _state.value
        if (current is SessionState.Active) {
            val endedAt = Instant.now()
            val resolvedNotes = notes ?: current.session.notes
            val ended = current.session.copy(
                endedAt = endedAt.toEpochMilli(),
                notes = resolvedNotes,
            )
            sessionDao.updateSession(ended)
            try {
                remoteDataSource.closeSession(
                    sessionId = ended.sessionId,
                    endedAt = endedAt,
                    notes = resolvedNotes,
                )
            } finally {
                _state.value = SessionState.Idle
            }
        } else {
            _state.value = SessionState.Idle
        }
    }
}
