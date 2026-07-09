package com.agarthavision.domain.usecase.capture

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject

@Suppress("LongParameterList")
class PersistFlaggedFrameUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleDao: SampleDao,
    private val sampleImageStore: SampleImageStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val gson: Gson,
) {
    suspend operator fun invoke(frame: FlaggedFrame): String {
        // Per ADR-007, capture works offline: attribute to the cached medtech when one
        // exists, else leave the sample unowned to be claimed at the next login. The image
        // is stored under the owner's folder, or a shared `local` folder when unowned.
        val ownerId = authRepository.currentLocalUserId()
        val sampleId = UUID.randomUUID().toString()
        val imagePath = sampleImageStore.persistJpeg(ownerId ?: UNOWNED_FOLDER, sampleId, frame.jpegBytes)
        val predictionsJson = frame.predictions
            .takeIf { it.isNotEmpty() }
            ?.let { gson.toJson(it) }
        val inferenceModelVersion = when (frame.source) {
            FrameSource.MANUAL -> frame.inferenceModelVersion ?: "manual"
            FrameSource.MODEL -> frame.inferenceModelVersion ?: "unknown"
        }

        sampleDao.insertSample(
            SampleEntity(
                sampleId = sampleId,
                sessionId = frame.sessionId,
                userId = ownerId,
                deviceId = deviceIdProvider.id,
                timestamp = frame.capturedAt.toEpochMilli(),
                verifiedAt = 0L,
                imagePath = imagePath,
                inferenceModelVersion = inferenceModelVersion,
                needsReannotation = false,
                gpsLatitude = null,
                gpsLongitude = null,
                gpsAccuracy = null,
                status = SampleStatus.FLAGGED.value,
                userNote = null,
                isManual = frame.source == FrameSource.MANUAL,
                isRepeat = frame.markedAsRepeat,
                predictionsJson = predictionsJson,
                imageWidth = frame.imageWidth,
                imageHeight = frame.imageHeight,
            )
        )

        return sampleId
    }

    private companion object {
        /** Storage sub-folder for frames captured before any medtech signed in. */
        const val UNOWNED_FOLDER = "local"
    }
}
