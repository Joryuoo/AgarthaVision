package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.RecordAuthor
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.isColleagueRecord
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.ColleagueRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientAccessRepository
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
    /** Who wrote the session. A colleague's is read-only here (14zcqntjph6). */
    val author: RecordAuthor = RecordAuthor.Viewer,
)

/**
 * Result of resolving session samples for the current device identity.
 *
 * Unowned rows ([Session.userId] == null) are visible to every caller on the device.
 * Owned rows are visible to the owner, and to every medtech assigned to the session's patient:
 * a colleague's smear is part of the patient's history (14zcqntjph5).
 *
 * The flow never emits a "loading" value — loading is "not yet emitted".
 */
sealed interface SessionSamplesResult {
    data class Visible(val data: SessionSamples) : SessionSamplesResult
    data object NotFound : SessionSamplesResult
    data object NotVisible : SessionSamplesResult
}

/**
 * Loads a session's samples and detections for the cached local identity.
 */
class GetSessionSamplesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val patientAccessRepository: PatientAccessRepository,
    private val colleagueRepository: ColleagueRepository,
) {
    operator fun invoke(sessionId: String): Flow<SessionSamplesResult> = flow {
        val userId = authRepository.currentLocalUserId()
        val session = sessionRepository.getSessionById(sessionId)
        if (session == null) {
            emit(SessionSamplesResult.NotFound)
            return@flow
        }
        if (!canRead(authorId = session.userId, sessionId = sessionId, userId = userId)) {
            emit(SessionSamplesResult.NotVisible)
            return@flow
        }

        val author = authorOf(session.userId, userId)
        emitAll(
            sampleRepository.observeSamplesForSession(sessionId, userId).map { samples ->
                SessionSamplesResult.Visible(
                    SessionSamples(
                        session = session,
                        author = author,
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

    /** Unowned, the reader's own, or on a patient the reader is assigned to. */
    private suspend fun canRead(authorId: String?, sessionId: String, userId: String?): Boolean {
        if (authorId == null || authorId == userId) return true
        return userId != null && patientAccessRepository.isAssignedToSessionPatient(sessionId, userId)
    }

    /** The viewer, or the colleague who wrote it — the second is read-only (14zcqntjph6). */
    private suspend fun authorOf(authorId: String?, userId: String?): RecordAuthor =
        if (authorId != null && isColleagueRecord(authorId, userId)) {
            RecordAuthor.Colleague(name = colleagueRepository.nameOf(authorId))
        } else {
            RecordAuthor.Viewer
        }
}
