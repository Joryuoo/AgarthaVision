package com.agarthavision.domain.usecase.auth

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import javax.inject.Inject

/**
 * Assigns ownership of unowned local data to a signed-in medtech so it can sync.
 *
 * Per ADR-007, at login all unowned, non-exempt sessions are claimed (or, in the manual
 * "Link to account" path, the explicitly-provided session ids), cascading to their
 * samples and reports. Only rows whose `user_id IS NULL` are touched, so the operation is
 * idempotent.
 *
 * Note: this reaches into DAOs directly, following the existing precedent of the verify
 * use cases (see [com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase]).
 * The shared local-sync abstraction is deferred to the Phase 2 WorkManager work — see
 * TODO.md.
 */
class ClaimLocalDataUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val reportDao: ReportDao,
) {
    /**
     * Claims local data for [userId].
     *
     * @param sessionIds When null, claims every unowned non-exempt session (the login
     * path). When provided, claims only those session ids (the manual link path).
     * @return [Result.success] with the number of sessions claimed, or [Result.failure].
     */
    suspend operator fun invoke(userId: String, sessionIds: List<String>? = null): Result<Int> =
        runCatching {
            val targetSessions = if (sessionIds == null) {
                sessionDao.getClaimableSessions().map { it.sessionId }
            } else {
                sessionIds
            }
            if (targetSessions.isEmpty()) {
                return@runCatching 0
            }

            if (sessionIds == null) {
                sessionDao.claimUnownedSessions(userId)
            } else {
                targetSessions.forEach { sessionDao.claimSession(it, userId) }
            }
            sampleDao.claimSamplesForSessions(targetSessions, userId)
            reportDao.claimReportsForSessions(targetSessions, userId)

            targetSessions.size
        }
}
