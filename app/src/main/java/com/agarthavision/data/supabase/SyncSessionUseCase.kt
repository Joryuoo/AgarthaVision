package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.domain.model.SessionSyncStatus
import javax.inject.Inject

/**
 * Synchronizes one pending session row from Room to Supabase.
 *
 * Row-only upsert (idempotent) so a session created or closed offline can be pushed once
 * connectivity and ownership are available. Mirrors the pattern of [SyncReportUseCase].
 * Per ADR-007.
 */
class SyncSessionUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val remoteDataSource: SessionRemoteDataSource,
) {
    /**
     * Pushes the session row to Supabase and updates the local sync state.
     *
     * @return [Result.success] when the row reaches [SessionSyncStatus.SYNCED], otherwise
     * [Result.failure] after marking the local row [SessionSyncStatus.SYNC_FAILED].
     */
    // Guard-clause early returns for the not-found/unowned precondition checks read more
    // clearly than nesting the runCatching block inside two if-expressions.
    @Suppress("ReturnCount")
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        val session = sessionDao.getSessionById(sessionId)
            ?: return Result.failure(IllegalArgumentException("Session $sessionId does not exist."))
        if (session.userId == null) {
            return Result.failure(IllegalStateException("Session $sessionId is unowned; claim it before syncing."))
        }

        return runCatching {
            remoteDataSource.upsertSession(session)
            sessionDao.updateSupabaseStatus(sessionId, SessionSyncStatus.SYNCED.value)
        }.onFailure {
            sessionDao.updateSupabaseStatus(sessionId, SessionSyncStatus.SYNC_FAILED.value)
        }
    }
}
