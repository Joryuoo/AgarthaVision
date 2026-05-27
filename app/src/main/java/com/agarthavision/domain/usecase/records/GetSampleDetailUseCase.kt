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
 * Loads one current-user sample and its detections.
 */
class GetSampleDetailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(sampleId: String): Flow<SampleRecordItem?> = flow {
        val userId = authRepository.getCurrentUserId()
        val sample = sampleRepository.getSampleById(sampleId)
        if (userId == null || sample?.userId != userId) {
            emit(null)
            return@flow
        }

        emitAll(
            detectionRepository.observeDetectionsForSample(sampleId).map { detections ->
                SampleRecordItem(sample = sample, detections = detections)
            },
        )
    }
}
