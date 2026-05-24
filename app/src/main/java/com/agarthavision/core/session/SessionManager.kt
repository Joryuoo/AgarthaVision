package com.agarthavision.core.session

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
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
 * `startSession()` inserts a [SessionEntity] in Room (and later in Supabase, in
 * Joryuoo's sync track) and transitions to [SessionState.Recording].
 * `stopSession()` marks `endedAt` and returns to [SessionState.Idle].
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.1.
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun startSession(userId: String? = null): SessionEntity {
        val now = Instant.now()
        val entity = SessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            deviceId = deviceIdProvider.id,
            startedAt = now.toEpochMilli(),
            endedAt = null,
            notes = null,
        )
        sessionDao.insertSession(entity)
        _state.value = SessionState.Recording(entity, now)
        return entity
    }

    suspend fun stopSession() {
        val current = _state.value
        if (current is SessionState.Recording) {
            val ended = current.session.copy(endedAt = Instant.now().toEpochMilli())
            sessionDao.updateSession(ended)
        }
        _state.value = SessionState.Idle
    }
}
