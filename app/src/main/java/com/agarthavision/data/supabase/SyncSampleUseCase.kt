package com.agarthavision.data.supabase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.SampleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Synchronizes one verified sample from Room to Supabase Storage and Postgres.
 */
class SyncSampleUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val remoteDataSource: SampleRemoteDataSource,
) {
    /**
     * Uploads the sample JPEG, inserts remote metadata rows, and updates local sync state.
     *
     * @return [Result.success] when the sample reaches [SampleStatus.SYNCED], otherwise
     * [Result.failure] after marking the local sample [SampleStatus.SYNC_FAILED].
     */
    suspend operator fun invoke(sampleId: String): Result<Unit> {
        val sample = sampleDao.getSampleById(sampleId)
            ?: return Result.failure(IllegalArgumentException("Sample $sampleId does not exist."))
        val detections = detectionDao.getDetectionsForSample(sampleId)

        return runCatching {
            val imageBytes = loadAndResizeJpeg(sample)
            val storagePath = remoteDataSource.syncSample(
                sample = sample,
                detections = detections,
                imageBytes = imageBytes,
            )
            sampleDao.updateSyncMetadata(
                sampleId = sampleId,
                status = SampleStatus.SYNCED.value,
                storagePath = storagePath,
            )
        }.onFailure {
            sampleDao.updateStatus(sampleId, SampleStatus.SYNC_FAILED.value)
        }
    }

    private suspend fun loadAndResizeJpeg(sample: SampleEntity): ByteArray =
        withContext(Dispatchers.IO) {
            val sourceFile = File(sample.imagePath)
            require(sourceFile.exists()) { "Sample image does not exist: ${sample.imagePath}" }
            resizeToSyncJpeg(sourceFile.readBytes())
        }

    private fun resizeToSyncJpeg(imageBytes: ByteArray): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
        if (boundsOptions.outWidth == SYNC_IMAGE_SIZE_PX && boundsOptions.outHeight == SYNC_IMAGE_SIZE_PX) {
            return imageBytes
        }

        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)) {
            "Sample image could not be decoded as a bitmap."
        }
        val resized = Bitmap.createScaledBitmap(bitmap, SYNC_IMAGE_SIZE_PX, SYNC_IMAGE_SIZE_PX, true)
        return ByteArrayOutputStream().use { output ->
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (resized !== bitmap) {
                resized.recycle()
            }
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private companion object {
        private const val SYNC_IMAGE_SIZE_PX = 640
        private const val JPEG_QUALITY = 80
    }
}
