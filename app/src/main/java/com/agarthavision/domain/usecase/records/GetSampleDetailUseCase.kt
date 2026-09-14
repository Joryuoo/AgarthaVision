package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
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
 * caller on the device. Owned rows are visible only to the owner.
 *
 * The flow never emits a "loading" value — loading is "not yet emitted".
 */
sealed interface SampleDetailResult {
    data class Visible(val data: SampleRecordItem) : SampleDetailResult
    data object NotFound : SampleDetailResult
    data object NotVisible : SampleDetailResult
}

/**
 * Loads one cached-identity sample and its detections.
 */
class GetSampleDetailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(sampleId: String): Flow<SampleDetailResult> = flow {
        val userId = authRepository.currentLocalUserId()
        val sample = sampleRepository.getSampleById(sampleId)
        if (sample == null) {
            emit(SampleDetailResult.NotFound)
            return@flow
        }
        if (!(sample.userId == null || sample.userId == userId)) {
            emit(SampleDetailResult.NotVisible)
            return@flow
        }

        emitAll(
            detectionRepository.observeDetectionsForSample(sampleId).map { detections ->
                SampleDetailResult.Visible(SampleRecordItem(sample = sample, detections = detections))
            },
        )
    }
}
