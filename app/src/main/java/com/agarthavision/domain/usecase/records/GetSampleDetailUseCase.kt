package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.RecordAuthor
import com.agarthavision.domain.model.isColleagueRecord
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.ColleagueRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientAccessRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Result of resolving a single sample for the current device identity.
 *
 * Unowned rows ([com.agarthavision.domain.model.Sample.userId] == null) are visible to every
 * caller on the device. Owned rows are visible to the owner, and to every medtech assigned to
 * the patient the sample's session belongs to (14zcqntjph5).
 *
 * The flow never emits a "loading" value — loading is "not yet emitted".
 */
sealed interface SampleDetailResult {
    /** [author] is who wrote the sample; a colleague's is read-only here (14zcqntjph6). */
    data class Visible(
        val data: SampleRecordItem,
        val author: RecordAuthor = RecordAuthor.Viewer,
    ) : SampleDetailResult
    data object NotFound : SampleDetailResult
    data object NotVisible : SampleDetailResult
}

/**
 * Loads one sample and its detections for the cached local identity.
 */
class GetSampleDetailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
    private val patientAccessRepository: PatientAccessRepository,
    private val colleagueRepository: ColleagueRepository,
) {
    operator fun invoke(sampleId: String): Flow<SampleDetailResult> = flow {
        val userId = authRepository.currentLocalUserId()
        val sample = sampleRepository.getSampleById(sampleId)
        if (sample == null) {
            emit(SampleDetailResult.NotFound)
            return@flow
        }
        if (!canRead(authorId = sample.userId, sessionId = sample.sessionId, userId = userId)) {
            emit(SampleDetailResult.NotVisible)
            return@flow
        }

        val author = authorOf(sample.userId, userId)
        emitAll(
            detectionRepository.observeDetectionsForSample(sampleId).map { detections ->
                SampleDetailResult.Visible(
                    data = SampleRecordItem(sample = sample, detections = detections),
                    author = author,
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
