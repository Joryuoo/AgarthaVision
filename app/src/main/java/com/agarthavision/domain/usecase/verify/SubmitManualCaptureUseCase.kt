package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.LocationProvider
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Persists a manual capture as a verified sample with a single confirmed detection.
 */
class SubmitManualCaptureUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val sampleImageStore: SampleImageStore,
    private val flaggedFrameStore: FlaggedFrameStore,
    private val locationProvider: LocationProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val syncSampleUseCase: SyncSampleUseCase,
) {
    /**
     * Creates a manual sample from [frame] using the provided species selection.
     */
    suspend operator fun invoke(
        frame: FlaggedFrame,
        species: EggSpecies?,
        otherSpeciesText: String,
        userNote: String?,
        isRepeat: Boolean,
    ): Result<String> = runCatching {
        val selectedSpecies = requireNotNull(species) { "Species is required." }
        val classLabel = selectedSpecies.canonicalClass ?: otherSpeciesText.trim()
        require(classLabel.isNotBlank()) { "Species label is required." }

        val userId = authRepository.getCurrentUserId()
            ?: error("A user session is required to submit manual capture.")
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
                inferenceModelVersion = frame.inferenceModelVersion ?: "manual",
                needsReannotation = true,
                gpsLatitude = location?.latitude,
                gpsLongitude = location?.longitude,
                gpsAccuracy = location?.accuracyMeters,
                status = SampleStatus.VERIFIED.value,
                userNote = userNote?.takeIf { it.isNotBlank() },
                isManual = true,
                isRepeat = isRepeat,
            ),
        )

        detectionDao.insertDetection(
            DetectionEntity(
                detectionId = UUID.randomUUID().toString(),
                sampleId = sampleId,
                classLabel = classLabel,
                confidence = 1.0f,
                bboxX = null,
                bboxY = null,
                bboxW = null,
                bboxH = null,
                verdict = DetectionVerdict.CONFIRMED.value,
                expertClass = classLabel,
                verifiedByUser = true,
            ),
        )

        if (isRepeat) {
            if (!frame.markedAsRepeat) {
                flaggedFrameStore.toggleRepeat(frame)
            }
        } else {
            flaggedFrameStore.remove(frame)
        }

        syncSampleUseCase.invoke(sampleId)

        sampleId
    }
}
