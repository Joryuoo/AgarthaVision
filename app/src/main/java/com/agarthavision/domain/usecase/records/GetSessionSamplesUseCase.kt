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
 * Loads current-user samples and detections for a session.
 */
class GetSessionSamplesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(sessionId: String): Flow<SessionSamples?> = flow {
        val userId = authRepository.getCurrentUserId()
        val session = sessionRepository.getSessionById(sessionId)
        if (userId == null || session?.userId != userId) {
            emit(null)
            return@flow
        }

        emitAll(
            sampleRepository.observeSamplesForSession(sessionId, userId).map { samples ->
                SessionSamples(
                    session = session,
                    samples = samples.map { sample ->
                        SampleRecordItem(
                            sample = sample,
                            detections = detectionRepository.getDetectionsForSample(sample.id),
                        )
                    },
                )
            },
        )
    }
}
