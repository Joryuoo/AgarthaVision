package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.mapper.toDetectionEntity
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.LocationProvider
import java.time.Instant
import javax.inject.Inject

@Suppress("LongParameterList")
class SubmitVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val locationProvider: LocationProvider,
    private val syncSampleUseCase: SyncSampleUseCase,
) {
    suspend operator fun invoke(
        frame: FlaggedFrame,
        answers: List<VerificationAnswers>,
        missedEgg: Boolean?,
        userNote: String? = null,
        isRepeat: Boolean = false,
    ): Result<String> = runCatching {
        authRepository.getCurrentUserId()
            ?: error("A user session is required to submit verification.")
        val sampleId = frame.sampleId
        require(sampleId.isNotBlank()) { "Flagged sample id is required." }
        val location = locationProvider.getCurrentLocation()
        val verifiedAt = Instant.now()

        sampleDao.updateSampleOnVerify(
            sampleId = sampleId,
            status = SampleStatus.VERIFIED.value,
            verifiedAt = verifiedAt.toEpochMilli(),
            needsReannotation = missedEgg == true,
            userNote = userNote?.takeIf { it.isNotBlank() },
            isRepeat = isRepeat,
            gpsLatitude = location?.latitude,
            gpsLongitude = location?.longitude,
            gpsAccuracy = location?.accuracyMeters,
        )

        val detections = frame.predictions.zip(answers).map { (prediction, answer) ->
            prediction.toDetectionEntity(sampleId, answer)
        }
        detectionDao.insertDetections(detections)

        syncSampleUseCase.invoke(sampleId)

        sampleId
    }
}
