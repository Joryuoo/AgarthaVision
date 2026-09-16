package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Detail payload for one recording session.
 */
data class SessionSamples(
    val session: Session,
    val samples: List<SampleRecordItem>,
)

/**
 * Result of resolving session samples for the current device identity.
 *
 * Unowned rows ([Session.userId] == null) are visible to every caller on the device.
 * Owned rows are visible only to the owner.
 *
 * The flow never emits a "loading" value — loading is "not yet emitted".
 */
sealed interface SessionSamplesResult {
    data class Visible(val data: SessionSamples) : SessionSamplesResult
    data object NotFound : SessionSamplesResult
    data object NotVisible : SessionSamplesResult
}

/**
 * Loads the cached local identity's samples and detections for a session.
 */
class GetSessionSamplesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(sessionId: String): Flow<SessionSamplesResult> = flow {
        val userId = authRepository.currentLocalUserId()
        val session = sessionRepository.getSessionById(sessionId)
        if (session == null) {
            emit(SessionSamplesResult.NotFound)
            return@flow
        }
        if (!(session.userId == null || session.userId == userId)) {
            emit(SessionSamplesResult.NotVisible)
            return@flow
        }

        emitAll(
            sampleRepository.observeSamplesForSession(sessionId, userId).map { samples ->
                SessionSamplesResult.Visible(
                    SessionSamples(
                        session = session,
                        samples = samples.map { sample ->
                            SampleRecordItem(
                                sample = sample,
                                detections = detectionRepository.getDetectionsForSample(sample.id),
                            )
                        },
                    ),
                )
            },
        )
    }
}
