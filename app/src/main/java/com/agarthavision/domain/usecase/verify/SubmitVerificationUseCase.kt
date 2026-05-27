package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.mapper.toDetectionEntity
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.LocationProvider
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@Suppress("LongParameterList")
class SubmitVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val sampleImageStore: SampleImageStore,
    private val flaggedFrameStore: FlaggedFrameStore,
    private val locationProvider: LocationProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val syncSampleUseCase: SyncSampleUseCase,
) {
    suspend operator fun invoke(
        frame: FlaggedFrame,
        answers: List<VerificationAnswers>,
        missedEgg: Boolean?,
    ): Result<String> = runCatching {
        val userId = authRepository.getCurrentUserId()
            ?: error("A user session is required to submit verification.")
        val sampleId = UUID.randomUUID().toString()
        val location = locationProvider.getCurrentLocation()
        val verifiedAt = Instant.now()

        val imagePath = sampleImageStore.persistJpeg(userId, sampleId, frame.jpegBytes)

        sampleDao.insertSample(
            SampleEntity(
                sampleId = sampleId,
                sessionId = frame.sessionId,
                userId = userId,
                deviceId = deviceIdProvider.id,
                timestamp = frame.capturedAt.toEpochMilli(),
                verifiedAt = verifiedAt.toEpochMilli(),
                imagePath = imagePath,
                inferenceModelVersion = frame.inferenceModelVersion ?: "unknown",
                needsReannotation = missedEgg == true,
                gpsLatitude = location?.latitude,
                gpsLongitude = location?.longitude,
                gpsAccuracy = location?.accuracyMeters,
                status = SampleStatus.VERIFIED.value,
            )
        )

        val detections = frame.predictions.zip(answers).map { (prediction, answer) ->
            prediction.toDetectionEntity(sampleId, answer)
        }
        detectionDao.insertDetections(detections)

        flaggedFrameStore.remove(frame)

        syncSampleUseCase.invoke(sampleId)

        sampleId
    }
}
