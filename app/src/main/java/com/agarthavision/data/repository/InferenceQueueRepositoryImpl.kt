package com.agarthavision.data.repository

import com.agarthavision.data.inference.encodePredictions
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.repository.InferenceQueueRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * [InferenceQueueRepository] over the `samples` table.
 *
 * Every write is one of `SampleDao`'s conditional UPDATEs; see the queue section there for why
 * that is enough to make cancel, delete and a landing result safe against each other.
 */
class InferenceQueueRepositoryImpl @Inject constructor(
    private val sampleDao: SampleDao,
    private val gson: Gson,
) : InferenceQueueRepository {

    override suspend fun requeueInterrupted() {
        sampleDao.requeueInterruptedInference()
    }

    override suspend fun queuedSampleIds(): List<String> = sampleDao.getQueuedInferenceSampleIds()

    override suspend fun claim(sampleId: String): Boolean = sampleDao.claimForInference(sampleId) == 1

    override suspend fun readFrame(sampleId: String): ByteArray? {
        val path = sampleDao.getImagePath(sampleId)?.takeIf { it.isNotBlank() } ?: return null
        return withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
    }

    override suspend fun complete(sampleId: String, result: InferenceResult): Boolean =
        sampleDao.completeInference(
            sampleId = sampleId,
            // Through the DTO, like capture always wrote it, so the column's shape does not
            // depend on which engine answered. Null for a clean field, as before.
            predictionsJson = gson.encodePredictions(result.predictions),
            // The engine's own version name, `…-cloud-fp32` or `…-tflite-fp32`, so every result
            // says which engine produced it. "unknown" only for an old container that sends none.
            modelVersion = result.modelVersion ?: UNKNOWN_MODEL_VERSION,
            imageWidth = result.imageWidth,
            imageHeight = result.imageHeight,
        ) == 1

    override suspend fun recordFailure(sampleId: String, maxAttempts: Int): InferenceState? {
        if (sampleDao.recordInferenceFailure(sampleId, maxAttempts) == 0) return null
        return InferenceState.fromValue(sampleDao.getEffectiveInferenceState(sampleId))
    }

    override suspend fun cancel(sampleId: String): Boolean = sampleDao.cancelInference(sampleId) == 1

    private companion object {
        /** Matches the default capture has always written for a model frame with no version. */
        const val UNKNOWN_MODEL_VERSION = "unknown"
    }
}
