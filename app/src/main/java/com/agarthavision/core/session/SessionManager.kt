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
 * pushed later by the sync trigger.
 *
 * **A session does not end.** One session is one fecal smear, and the medtech keeps coming back
 * to it - correcting a sample, generating a report from whatever is verified so far. There is
 * no `stopSession`, and nothing in the app writes `sessions.ended_at` any more. The column and
 * its nullability stay: sessions closed before this change are real history and must not be
 * rewritten, `SessionRemoteDataSource.closeSession` still exists for them, and [resumeSession]
 * still refuses to reopen one.
 *
 * What replaces ending is [clearActive], which detaches the app from a session without
 * declaring it finished.
 *
 * See CONTEXT.md and ticket 86d4ab4vm.
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val remoteDataSource: SessionRemoteDataSource,
    private val authRepository: AuthRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val activeSessionIdStore: ActiveSessionIdStore,
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
     * @param psgcBarangayCode The patient's barangay as a zero-padded 10-digit PSGC code.
     *   Null only for callers that predate the picker; the Sessions UI always supplies it.
     * @param notes Optional in-session observations (slide condition, prep quality, etc.).
     * @return The locally persisted session row.
     */
    suspend fun startSession(
        label: String,
        psgcBarangayCode: String? = null,
        notes: String? = null,
    ): SessionEntity {
        val now = Instant.now()
        // Never null: login is mandatory on first run, so an identity is cached before any
        // screen that could reach here exists. Asserting it is the point — a session with no
        // owner cannot be pushed, and silently creating one would strand the smear on the
        // device with no error anywhere.
        val ownerId = requireNotNull(authRepository.currentLocalUserId()) {
            "startSession called with no cached identity; the first-run login gate should " +
                "have made that impossible."
        }
        val entity = SessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = ownerId,
            deviceId = deviceIdProvider.id,
            startedAt = now.toEpochMilli(),
            endedAt = null,
            notes = notes,
            label = label,
            psgcBarangayCode = psgcBarangayCode,
            supabaseStatus = SessionSyncStatus.PENDING.value,
        )
        sessionDao.insertSession(entity)
        val synced = pushSessionInsert(entity)
        activate(synced, now)
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
        activate(entity, Instant.ofEpochMilli(entity.startedAt))
        return entity
    }

    /**
     * Re-attaches to the session the medtech was last working in, if it is still open.
     *
     * Called once at app start. Without it a process restart would leave the app idle while a
     * session is still live, and the verification queue would render empty - see
     * [ActiveSessionIdStore].
     *
     * A stored id that no longer resolves, or resolves to a session ended before this change,
     * clears itself rather than throwing: the pointer is a convenience, and failing to restore
     * it must never stop the app launching.
     */
    suspend fun restoreActiveSession(): SessionEntity? {
        val storedId = activeSessionIdStore.read() ?: return null
        return runCatching { resumeSession(storedId) }
            .getOrElse {
                activeSessionIdStore.write(null)
                null
            }
    }

    /**
     * Detaches from the active session **without ending it**.
     *
     * The session stays open and the medtech can come back to it; this only says the app is no
     * longer working in it. Used by sign-out, where the alternative - blocking until the
     * session ends - became impossible once sessions stopped ending. Deliberately writes no
     * `ended_at`.
     */
    suspend fun clearActive() {
        activeSessionIdStore.write(null)
        _state.value = SessionState.Idle
    }

    private suspend fun activate(session: SessionEntity, startedAt: Instant) {
        activeSessionIdStore.write(session.sessionId)
        _state.value = SessionState.Active(session, startedAt)
    }

    /**
     * Pushes the local session row to Supabase. Returns the entity with its resolved
     * [SessionSyncStatus]; never throws.
     *
     * The claim-exempt half of this guard is gone with the opt-out itself. The null-owner
     * half is now unreachable via [startSession] — it is kept only because
     * `SessionEntity.userId` is still typed nullable for rows pulled from Supabase.
     */
    private suspend fun pushSessionInsert(entity: SessionEntity): SessionEntity {
        if (entity.userId == null) {
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
