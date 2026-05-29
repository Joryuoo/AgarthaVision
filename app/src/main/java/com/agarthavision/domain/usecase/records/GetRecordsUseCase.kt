package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
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
 * Session summary shown on the Records screen.
 */
data class SessionRecordItem(
    val session: Session,
    val sampleCount: Int,
    val speciesLabels: List<String>,
    val latitude: Double?,
    val longitude: Double?,
    val totalEpg: Int = 0,
)

/**
 * Sample row with detections attached for session detail and sample detail views.
 */
data class SampleRecordItem(
    val sample: Sample,
    val detections: List<Detection>,
) {
    val primaryDetection: Detection?
        get() = detections
            .filterNot { it.verdict == DetectionVerdict.FALSE_POSITIVE }
            .maxByOrNull { it.confidence }
            ?: detections.maxByOrNull { it.confidence }
}

/**
 * Provides current-user session summaries for the Records screen.
 */
class GetRecordsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(): Flow<List<SessionRecordItem>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }

        emitAll(
            sessionRepository.observeAllSessions(userId).map { sessions ->
                sessions.map { session ->
                    val samples = sampleRepository.getSamplesForSession(session.id, userId)
                    val detections = samples.flatMap { sample ->
                        detectionRepository.getDetectionsForSample(sample.id)
                    }
                    val countedSampleIds = samples
                        .filterNot { it.isRepeat }
                        .map { it.id }
                        .toSet()
                    val totalEpg = detections.count { detection ->
                        detection.sampleId in countedSampleIds &&
                            detection.verdict != DetectionVerdict.FALSE_POSITIVE
                    }
                    val speciesLabels = detections
                        .mapNotNull { detection -> detection.expertClass ?: detection.classLabel }
                        .distinct()
                        .sorted()
                    SessionRecordItem(
                        session = session,
                        sampleCount = samples.size,
                        speciesLabels = speciesLabels,
                        latitude = samples.firstNotNullOfOrNull { it.latitude },
                        longitude = samples.firstNotNullOfOrNull { it.longitude },
                        totalEpg = totalEpg,
                    )
                }
            },
        )
    }
}
