package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.DetectionEntity
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
    private val locationProvider: LocationProvider,
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

        authRepository.getCurrentUserId()
            ?: error("A user session is required to submit manual capture.")
        val sampleId = frame.sampleId
        require(sampleId.isNotBlank()) { "Flagged sample id is required." }
        val location = locationProvider.getCurrentLocation()
        val verifiedAt = Instant.now()

        sampleDao.updateSampleOnVerify(
            sampleId = sampleId,
            status = SampleStatus.VERIFIED.value,
            verifiedAt = verifiedAt.toEpochMilli(),
            needsReannotation = true,
            userNote = userNote?.takeIf { it.isNotBlank() },
            isRepeat = isRepeat,
            gpsLatitude = location?.latitude,
            gpsLongitude = location?.longitude,
            gpsAccuracy = location?.accuracyMeters,
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

        syncSampleUseCase.invoke(sampleId)

        sampleId
    }
}
