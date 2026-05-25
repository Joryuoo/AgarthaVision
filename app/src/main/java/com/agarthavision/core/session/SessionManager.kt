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
 * App-scoped tracker of the active recording session.
 *
 * `startSession()` inserts a [SessionEntity] in Room and Supabase, then
 * transitions to [SessionState.Recording]. `stopSession()` marks `endedAt`
 * locally and remotely before returning to [SessionState.Idle].
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
     * Starts a recording session for the currently authenticated Supabase user.
     *
     * @return The locally persisted session row mirrored to Supabase.
     * @throws IllegalStateException when no Supabase user session is available.
     */
    suspend fun startSession(): SessionEntity {
        val now = Instant.now()
        val userId = remoteDataSource.currentUserId()
            ?: error("A Supabase user session is required to start a recording session.")
        val entity = SessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            deviceId = deviceIdProvider.id,
            startedAt = now.toEpochMilli(),
            endedAt = null,
            notes = null,
        )
        sessionDao.insertSession(entity)
        runCatching {
            remoteDataSource.insertSession(entity)
        }.onFailure {
            sessionDao.updateSession(entity.copy(endedAt = Instant.now().toEpochMilli()))
            throw it
        }
        _state.value = SessionState.Recording(entity, now)
        return entity
    }

    /**
     * Stops the active recording session, if one exists.
     */
    suspend fun stopSession() {
        val current = _state.value
        if (current is SessionState.Recording) {
            val endedAt = Instant.now()
            val ended = current.session.copy(endedAt = endedAt.toEpochMilli())
            sessionDao.updateSession(ended)
            try {
                remoteDataSource.closeSession(
                    sessionId = ended.sessionId,
                    endedAt = endedAt,
                )
            } finally {
                _state.value = SessionState.Idle
            }
        } else {
            _state.value = SessionState.Idle
        }
    }
}
