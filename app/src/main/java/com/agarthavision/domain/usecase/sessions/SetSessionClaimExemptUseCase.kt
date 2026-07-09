package com.agarthavision.domain.usecase.sessions

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.domain.model.SessionSyncStatus
import javax.inject.Inject

/**
 * Toggles whether a session is claimed by / linked to the current account.
 *
 * Per ADR-007, this backs the per-session "Link to account" control:
 * - `exempt = true` opts an unowned session out of being claimed at the next login, or
 *   unlinks an already-claimed session back to unowned — allowed **only while the session
 *   is still pending sync** (once synced, the cloud row exists and ownership is permanent).
 * - `exempt = false` clears the opt-out so the session is claimed again.
 */
class SetSessionClaimExemptUseCase @Inject constructor(
    private val sessionDao: SessionDao,
) {
    /**
     * Applies the claim-exempt [exempt] flag to the session.
     *
     * @return [Result.failure] when attempting to unlink a session that has already
     * synced (ownership is permanent at that point), otherwise [Result.success].
     */
    suspend operator fun invoke(sessionId: String, exempt: Boolean): Result<Unit> =
        runCatching {
            val session = sessionDao.getSessionById(sessionId)
                ?: error("Session $sessionId not found locally.")

            val isClaimed = session.userId != null
            val alreadySynced = session.supabaseStatus == SessionSyncStatus.SYNCED.value
            if (exempt && isClaimed) {
                check(!alreadySynced) {
                    "Cannot unlink session $sessionId: it has already synced to the cloud."
                }
                // Revert a still-pending claimed session back to unowned + exempt.
                sessionDao.updateSession(
                    session.copy(
                        userId = null,
                        claimExempt = true,
                        supabaseStatus = SessionSyncStatus.PENDING.value,
                    ),
                )
            } else {
                sessionDao.setClaimExempt(sessionId, exempt)
            }
        }
}
