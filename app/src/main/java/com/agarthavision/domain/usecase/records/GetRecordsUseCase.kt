package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class RecordItem(
    val sample: Sample,
    val primaryDetection: Detection?,
)

class GetRecordsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(): Flow<List<RecordItem>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
        } else {
            emitAll(
                sampleRepository.observeAllSamples(userId).map { samples ->
                    samples.map { sample ->
                        val detections = detectionRepository.getDetectionsForSample(sample.id)
                        // Primary detection is the one with highest confidence among non-false-positives
                        val primary = detections
                            .filter { it.verdict.value != "false_positive" }
                            .maxByOrNull { it.confidence }

                        RecordItem(sample, primary)
                    }
                }
            )
        }
    }
}
