package com.agarthavision.data.supabase

import android.util.Log
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
    private val syncPatientUseCase: SyncPatientUseCase,
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
        if (session == null) {
            val errorMsg = "Session $sessionId does not exist."
            Log.e(TAG, "[SyncFailed][Session:$sessionId][Class:MISSING_ENTITY] $errorMsg")
            return Result.failure(IllegalArgumentException(errorMsg))
        }
        if (session.userId == null) {
            val errorMsg = "Session $sessionId is unowned; claim it before syncing."
            Log.e(TAG, "[SyncFailed][Session:$sessionId][Class:UNOWNED_SESSION] $errorMsg")
            return Result.failure(IllegalStateException(errorMsg))
        }

        if (session.supabaseStatus == SessionSyncStatus.SYNCED.value) {
            return Result.success(Unit)
        }

        return runCatching {
            // Ensure parent patient is synced before pushing session to prevent FK violation
            if (session.patientId.isNotBlank()) {
                val patientSyncResult = syncPatientUseCase(session.patientId)
                if (patientSyncResult.isFailure) {
                    val cause = patientSyncResult.exceptionOrNull()
                    throw IllegalStateException("Parent patient ${session.patientId} failed to sync prior to session $sessionId", cause)
                }
            }

            remoteDataSource.upsertSession(session)
            sessionDao.updateSupabaseStatus(sessionId, SessionSyncStatus.SYNCED.value)
        }.onFailure { throwable ->
            val failureClass = classifyFailure(throwable)
            Log.e(
                TAG,
                "[SyncFailed][Session:$sessionId][Patient:${session.patientId}][Class:$failureClass] Marking status SYNC_FAILED. Error: ${throwable.message}",
                throwable,
            )
            sessionDao.updateSupabaseStatus(sessionId, SessionSyncStatus.SYNC_FAILED.value)
        }
    }

    private fun classifyFailure(throwable: Throwable): String = when {
        throwable is java.net.UnknownHostException || throwable is java.io.IOException -> "NETWORK_ERROR"
        throwable is IllegalStateException && throwable.message?.contains("Parent patient", ignoreCase = true) == true -> "PARENT_PATIENT_SYNC_FAILED"
        throwable is IllegalStateException && throwable.message?.contains("session", ignoreCase = true) == true -> "UNAUTHENTICATED"
        throwable.message?.contains("foreign key constraint", ignoreCase = true) == true -> "FOREIGN_KEY_VIOLATION"
        else -> throwable.javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
    }

    private companion object {
        const val TAG = "SyncSessionUseCase"
    }
}
