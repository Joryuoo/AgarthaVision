package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.QueueSample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import javax.inject.Inject

/**
 * The verification queue: the unverified samples in the open session, and nothing else.
 *
 * This partially reverses 86d4ab4vm, **deliberately**. That ticket made the queue show the union
 * of verified and unverified samples in two buckets, so a verified sample stayed visible and
 * stayed editable in place. Under the patient-records plan, editing a verified sample moves to
 * the Sample Data Screen, reached from the Records screen's Samples tab, so the queue goes back
 * to meaning one thing: work still to do. The capability was relocated, not removed.
 *
 * **[SampleStatus.FLAGGED] is the single definition of "unverified" in the app**, and it lives
 * here. The queue no longer carries an `isVerified` field for a reader to re-derive: the
 * predicate produces the list, so a row in the list cannot disagree with it.
 *
 * Unverified samples are held **locally only** — `getSamplesPendingSync` selects `verified` and
 * `sync_failed`, so nothing in this queue has been or will be pushed until a human submits it
 * (C7). Emptying this list is the only thing that puts a sample on the wire.
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
    operator fun invoke(): Flow<VerificationQueue> =
        combine(
            authRepository.observeLocalIdentity(),
            sessionManager.state,
        ) { identity, session -> identity?.userId to session }
            .flatMapLatest { (userId, session) ->
                when (session) {
                    // No open session, nothing to review. Not an error state.
                    SessionState.Idle -> flowOf(VerificationQueue())
                    is SessionState.Active -> {
                        val sessionId = session.session.sessionId
                        combine(
                            sampleDao.observeFlaggedSamplesForSession(sessionId, userId),
                            sampleDao.observeVerifiedCountForSession(sessionId, userId),
                        ) { flagged, verifiedCount ->
                            VerificationQueue(
                                samples = flagged.map { it.toQueueSample() },
                                verifiedInSession = verifiedCount,
                                sessionId = sessionId,
                            )
                        }
                    }
                }
            }

    private fun SampleEntity.toQueueSample(): QueueSample = QueueSample(
        sampleId = sampleId,
        capturedAt = Instant.ofEpochMilli(timestamp),
        imagePath = imagePath,
        source = if (isManual) FrameSource.MANUAL else FrameSource.MODEL,
    )
}

/**
 * What the verification queue screen needs to render itself.
 *
 * [verifiedInSession] is here rather than derived from [samples] because it cannot be: the list
 * holds unverified rows only, so an empty list is silent about whether anything was ever
 * captured. The two empty states mean opposite things to the medtech — "start capturing" versus
 * "you are done, here are the records" (86d4ayefd) — and this count is what tells them apart.
 *
 * [sessionId] travels with the queue rather than being read off the first row, which an empty
 * queue does not have. The route onward from the all-done state needs it precisely when there is
 * nothing in the list to read it from.
 */
data class VerificationQueue(
    val samples: List<QueueSample> = emptyList(),
    val verifiedInSession: Int = 0,
    val sessionId: String? = null,
)
