package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.QueueSample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * The verification queue: every live sample in the open session, verified or not.
 *
 * Replaces reading `FlaggedFrameStore` for this screen, which only ever held the unverified
 * half. That also takes the ViewModel off a data-layer singleton and back onto a use case (C1);
 * `FlaggedFrameStore` still serves the capture screen's badge and toast.
 *
 * Reads DAOs directly, like `SubmitVerificationUseCase` and the other ten `domain/` files
 * `docs/constraints.md` C3 already documents. Consistent with its neighbours rather than
 * inventing a repository for one caller.
 */
class ObserveVerificationQueueUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<QueueSample>> =
        combine(
            authRepository.observeLocalIdentity(),
            sessionManager.state,
        ) { identity, session -> identity?.userId to session }
            .flatMapLatest { (userId, session) ->
                when (session) {
                    // No open session, nothing to review. Not an error state.
                    SessionState.Idle -> flowOf(emptyList())
                    is SessionState.Active ->
                        sampleDao.observeQueueRowsForSession(session.session.sessionId, userId)
                            .map { rows -> rows.map { it.toQueueSample() } }
                }
            }

    private fun com.agarthavision.data.local.dao.QueueSampleRow.toQueueSample(): QueueSample {
        val entity = sample
        // status != flagged, so the three non-flagged states all read as verified. That makes
        // the derivation monotonic for free: an edit moves SYNCED -> VERIFIED and a failed push
        // moves VERIFIED -> SYNC_FAILED, and neither crosses back. Verification is one-way.
        val isVerified = entity.status != SampleStatus.FLAGGED.value
        return QueueSample(
            sampleId = entity.sampleId,
            sessionId = entity.sessionId,
            capturedAt = Instant.ofEpochMilli(entity.timestamp),
            imagePath = entity.imagePath,
            source = if (entity.isManual) FrameSource.MANUAL else FrameSource.MODEL,
            isVerified = isVerified,
            verifiedAt = entity.verifiedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            confirmedDetections = confirmedDetections,
            userNote = entity.userNote,
        )
    }
}
