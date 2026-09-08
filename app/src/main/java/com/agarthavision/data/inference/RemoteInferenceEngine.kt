package com.agarthavision.data.inference

import com.agarthavision.data.remote.InferenceApi
import com.agarthavision.domain.inference.InferenceEngine
import com.agarthavision.domain.inference.InferenceEngineId
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceTimings
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import com.agarthavision.domain.usecase.inference.NetworkErrorMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cloud backend: the self-hosted FastAPI container reached over HTTP.
 *
 * This is the behaviour that used to live inline in `InferFrameUseCase`, moved behind
 * [InferenceEngine] so the on-device path can stand beside it. The only functional change is
 * that transport errors now route through [NetworkErrorMapper] instead of being wrapped by
 * hand, which is what that class was written for.
 */
@Singleton
class RemoteInferenceEngine @Inject constructor(
    private val inferenceApi: InferenceApi,
    private val networkErrorMapper: NetworkErrorMapper,
) : InferenceEngine {

    override val id: InferenceEngineId = InferenceEngineId.REMOTE

    override suspend fun isAvailable(): Boolean = runCatching {
        inferenceApi.health().isSuccessful
    }.getOrDefault(false)

    override suspend fun infer(jpegBytes: ByteArray): InferenceResult {
        val startedAt = System.nanoTime()

        val response = networkErrorMapper.execute {
            inferenceApi.infer(jpegBytes.toRequestBody(JPEG_MEDIA_TYPE.toMediaType()))
        }
        if (!response.isSuccessful) {
            throw InferenceConnectionException(
                IllegalStateException("Inference failed: ${response.code()}"),
            )
        }

        val totalMs = elapsedMsSince(startedAt)
        val body = response.body()

        // The container reports its own compute time when it is new enough to send it. That
        // lets the benchmark compare server compute against device compute rather than
        // against the medtech's wifi. Older containers omit it and the split degrades to
        // "all of it was network", which is honest rather than wrong.
        val serverInferMs = body?.inferenceMs?.toLong() ?: 0L

        return InferenceResult(
            predictions = body?.predictions.orEmpty().toDomainPredictions(),
            imageWidth = body?.image?.width,
            imageHeight = body?.image?.height,
            modelVersion = body?.modelVersion,
            engine = id,
            timings = InferenceTimings(
                inferMs = serverInferMs,
                networkMs = (totalMs - serverInferMs).coerceAtLeast(0),
                totalMs = totalMs,
            ),
        )
    }

    private companion object {
        const val JPEG_MEDIA_TYPE = "image/jpeg"

        fun elapsedMsSince(startNanos: Long): Long =
            (System.nanoTime() - startNanos) / NANOS_PER_MILLI

        const val NANOS_PER_MILLI = 1_000_000L
    }
}
