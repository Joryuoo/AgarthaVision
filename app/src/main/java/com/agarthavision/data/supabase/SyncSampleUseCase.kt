package com.agarthavision.data.supabase

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.inference.decodePredictions
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.mapper.toSamplePredictions
import com.agarthavision.domain.model.SampleStatus
import com.google.gson.Gson
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
    private val findingDao: SampleSpeciesFindingDao,
    private val remoteDataSource: SampleRemoteDataSource,
    private val syncSessionUseCase: SyncSessionUseCase,
    private val gson: Gson,
) {
    /**
     * Uploads the sample JPEG, inserts remote metadata rows, and updates local sync state.
     *
     * @return [Result.success] when the sample reaches [SampleStatus.SYNCED], otherwise
     * [Result.failure] after marking the local sample [SampleStatus.SYNC_FAILED].
     */
    @Suppress("ReturnCount")
    suspend operator fun invoke(sampleId: String): Result<Unit> {
        // Including deleted: a tombstoned sample still has to push its tombstone, and reading
        // it through the filtered accessor would make the delete local-only.
        val sample = sampleDao.getSampleByIdIncludingDeleted(sampleId)
        if (sample == null) {
            val errorMsg = "Sample $sampleId does not exist."
            Log.e(TAG, "[SyncFailed][Sample:$sampleId][Class:MISSING_ENTITY] $errorMsg")
            return Result.failure(IllegalArgumentException(errorMsg))
        }

        if (sample.status == SampleStatus.SYNCED.value) {
            return Result.success(Unit)
        }

        val detections = detectionDao.getDetectionsForSample(sampleId)
        val findings = findingDao.getFindingsForSample(sampleId)

        return runCatching {
            // Ensure parent session (and patient) is synced before pushing sample to avoid FK race
            if (sample.sessionId.isNotBlank()) {
                val sessionSyncResult = syncSessionUseCase(sample.sessionId)
                if (sessionSyncResult.isFailure) {
                    val cause = sessionSyncResult.exceptionOrNull()
                    val msg = "Parent session ${sample.sessionId} failed to sync " +
                        "prior to sample $sampleId"
                    throw IllegalStateException(msg, cause)
                }
            }

            val imageBytes = loadAndResizeJpeg(sample)
            // Decoded inside the runCatching: an unreadable column fails this sample's push and
            // marks it sync_failed, loudly, rather than pushing the sample with its model output
            // silently dropped.
            val predictions = gson.decodePredictions(sample.predictionsJson)
                .orEmpty()
                .toSamplePredictions(sampleId)
            val storagePath = remoteDataSource.syncSample(
                sample = sample,
                detections = detections,
                findings = findings,
                predictions = predictions,
                imageBytes = imageBytes,
            )
            sampleDao.updateSyncMetadata(
                sampleId = sampleId,
                status = SampleStatus.SYNCED.value,
                storagePath = storagePath,
            )
        }.onFailure { throwable ->
            val failureClass = classifyFailure(throwable)
            Log.e(
                TAG,
                "[SyncFailed][Sample:$sampleId][Session:${sample.sessionId}]" +
                    "[Class:$failureClass] Marking status SYNC_FAILED. Error: ${throwable.message}",
                throwable,
            )
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

    private fun classifyFailure(throwable: Throwable): String {
        val msg = throwable.message.orEmpty()
        return when {
            throwable is java.io.FileNotFoundException ||
                msg.contains("image does not exist", ignoreCase = true) ->
                "FILE_NOT_FOUND"
            throwable is IllegalStateException &&
                msg.contains("Parent session", ignoreCase = true) ->
                "PARENT_SESSION_SYNC_FAILED"
            throwable is IllegalStateException &&
                msg.contains("user session", ignoreCase = true) ->
                "UNAUTHENTICATED"
            throwable is java.net.UnknownHostException ||
                throwable is java.io.IOException ->
                "NETWORK_ERROR"
            msg.contains("foreign key constraint", ignoreCase = true) ->
                "FOREIGN_KEY_VIOLATION"
            else ->
                throwable.javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
        }
    }

    private companion object {
        const val TAG = "SyncSampleUseCase"
        private const val SYNC_IMAGE_SIZE_PX = 640
        private const val JPEG_QUALITY = 80
    }
}
