package com.agarthavision.core.session

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
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
 * Per ADR-005, a session = one fecal smear. Per ADR-007, session creation is now
 * **offline-first**: [startSession] always writes a local row (owner = cached identity or
 * null when never signed in) and pushes to Supabase best-effort — a remote failure leaves
 * the session [SessionSyncStatus.PENDING] rather than rolling it back. The pending row is
 * pushed later by the sync trigger. [stopSession] ends the session locally regardless of
 * connectivity.
 *
 * See CONTEXT.md and TODO.md
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val remoteDataSource: SessionRemoteDataSource,
    private val authRepository: AuthRepository,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)

    /**
     * Current app-level capture session state.
     */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Starts a capture session, attributing it to the cached medtech when one exists.
     *
     * Works fully offline: the local row is always written. If a Supabase user session is
     * available the row is pushed immediately and marked [SessionSyncStatus.SYNCED];
     * otherwise it stays [SessionSyncStatus.PENDING] for the next sync pass. Never throws
     * on a missing auth session or a failed remote push.
     *
     * @param label The fecal-smear name the medtech entered in the picker.
     * @param notes Optional in-session observations (slide condition, prep quality, etc.).
     * @return The locally persisted session row.
     */
    suspend fun startSession(label: String, notes: String? = null): SessionEntity {
        val now = Instant.now()
        val ownerId = authRepository.currentLocalUserId()
        val entity = SessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = ownerId,
            deviceId = deviceIdProvider.id,
            startedAt = now.toEpochMilli(),
            endedAt = null,
            notes = notes,
            label = label,
            supabaseStatus = SessionSyncStatus.PENDING.value,
            claimExempt = false,
        )
        sessionDao.insertSession(entity)
        val synced = pushSessionInsert(entity)
        _state.value = SessionState.Active(synced, now, isInferenceRunning = true)
        return synced
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
     * Ends the active session, if one exists. The local row is always updated with
     * `ended_at`; the remote close is best-effort so ending works offline. A remote
     * failure leaves the row [SessionSyncStatus.PENDING] for the next sync pass. Optional
     * [notes] override lets the End-Session confirmation dialog save final observations.
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
            if (ended.userId != null && !ended.claimExempt) {
                runCatching {
                    remoteDataSource.closeSession(
                        sessionId = ended.sessionId,
                        endedAt = endedAt,
                        notes = resolvedNotes,
                    )
                }.onFailure {
                    sessionDao.updateSupabaseStatus(ended.sessionId, SessionSyncStatus.PENDING.value)
                }
            }
        }
        _state.value = SessionState.Idle
    }

    /**
     * Pushes the local session row to Supabase when an owner is set and not opted out.
     * Returns the entity with its resolved [SessionSyncStatus]; never throws.
     */
    private suspend fun pushSessionInsert(entity: SessionEntity): SessionEntity {
        if (entity.userId == null || entity.claimExempt) {
            return entity
        }
        return runCatching {
            remoteDataSource.upsertSession(entity)
            val synced = entity.copy(supabaseStatus = SessionSyncStatus.SYNCED.value)
            sessionDao.updateSession(synced)
            synced
        }.getOrElse { entity }
    }
}
