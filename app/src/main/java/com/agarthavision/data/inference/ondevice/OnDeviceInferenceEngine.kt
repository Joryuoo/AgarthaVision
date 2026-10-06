package com.agarthavision.data.inference.ondevice

import com.agarthavision.core.util.Logger
import com.agarthavision.domain.inference.InferenceEngine
import com.agarthavision.domain.inference.InferenceEngineId
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceTimings
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The on-device backend: a bundled TFLite model run by LiteRT on this phone.
 *
 * The model is compiled once, on first use, and kept: compiling means mapping ~40 MB and
 * building GPU programs, and paying that per frame would dominate every measurement. A failed
 * load is remembered too, so an unusable model costs one attempt rather than one per frame.
 *
 * Everything touching the model runs on one dedicated thread. A compiled model is not
 * thread-safe, and the GPU accelerator is happiest when it is always called from the same
 * thread; a single-thread dispatcher gives both, and serialises frames for free.
 *
 * Returns the same shape as the cloud engine: centre-based boxes in source-image pixels, no
 * clinical filtering (C7), and the manifest's version name as the model version.
 *
 * Built by `InferenceModule`, not injected directly, so the benchmark test can construct one
 * per precision.
 */
class OnDeviceInferenceEngine(
    private val modelStore: ModelStore,
    private val preprocessor: FramePreprocessor,
    private val decoder: YoloOutputDecoder,
    private val modelName: String,
) : InferenceEngine {

    override val id: InferenceEngineId = InferenceEngineId.ON_DEVICE

    private val dispatcher: ExecutorCoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable -> Thread(runnable, "on-device-inference") }
        .asCoroutineDispatcher()

    private var state: State = State.NotLoaded

    override suspend fun isAvailable(): Boolean = withContext(dispatcher) { ensureLoaded() != null }

    override suspend fun infer(jpegBytes: ByteArray): InferenceResult = withContext(dispatcher) {
        val session = ensureLoaded() ?: throw InferenceConnectionException(
            IllegalStateException("On-device model $modelName is unavailable"),
        )
        run(session, jpegBytes)
    }

    private fun ensureLoaded(): Session? = when (val current = state) {
        is State.Loaded -> current.session
        State.Failed -> null
        State.NotLoaded -> load()
    }

    private fun load(): Session? {
        val loaded = modelStore.load(modelName)
        val session = loaded?.let { model ->
            runCatching {
                Session(model, model.model.createInputBuffers(), model.model.createOutputBuffers())
            }.onFailure { Logger.w(TAG, "Allocating buffers for $modelName failed.", it) }.getOrNull()
        }

        if (session == null) {
            loaded?.model?.close()
            state = State.Failed
            Logger.w(TAG, "On-device inference unavailable: $modelName could not be loaded.")
        } else {
            state = State.Loaded(session)
            Logger.i(
                TAG,
                "Loaded ${session.manifest.modelVersion} on ${session.loaded.accelerators} " +
                    "in ${session.loaded.loadMs} ms",
            )
        }
        return session
    }

    private fun run(session: Session, jpegBytes: ByteArray): InferenceResult {
        val manifest = session.manifest
        val startedAt = System.nanoTime()

        val frame = preprocessor.preprocess(jpegBytes, manifest)
            ?: throw InferenceConnectionException(IllegalArgumentException("Frame could not be decoded"))
        val preprocessMs = elapsedMsSince(startedAt)

        val inferStartedAt = System.nanoTime()
        val output = runCatching {
            session.inputs[0].writeFloat(frame.pixels)
            session.loaded.model.run(session.inputs, session.outputs)
            session.outputs[0].readFloat()
        }.getOrElse { throw InferenceConnectionException(it) }
        val inferMs = elapsedMsSince(inferStartedAt)

        val postStartedAt = System.nanoTime()
        val predictions = decoder.decode(output, frame.transform, manifest)
        val postprocessMs = elapsedMsSince(postStartedAt)
        val totalMs = elapsedMsSince(startedAt)

        // The per-frame latency record the precision decision is made from.
        Logger.i(
            TAG,
            "${manifest.modelVersion} on ${session.loaded.accelerators}: " +
                "pre=$preprocessMs infer=$inferMs post=$postprocessMs total=$totalMs ms, " +
                "${predictions.size} boxes",
        )

        return InferenceResult(
            predictions = predictions,
            imageWidth = frame.transform.sourceWidth,
            imageHeight = frame.transform.sourceHeight,
            modelVersion = manifest.modelVersion,
            engine = id,
            counts = predictions.groupingBy { it.classLabel }.eachCount(),
            timings = InferenceTimings(
                preprocessMs = preprocessMs,
                inferMs = inferMs,
                postprocessMs = postprocessMs,
                totalMs = totalMs,
            ),
        )
    }

    /** A loaded model with its I/O buffers, allocated once and reused for every frame. */
    private class Session(
        val loaded: LoadedModel,
        val inputs: List<TensorBuffer>,
        val outputs: List<TensorBuffer>,
    ) {
        val manifest: ModelManifest get() = loaded.manifest
    }

    private sealed interface State {
        data object NotLoaded : State
        data object Failed : State
        class Loaded(val session: Session) : State
    }

    private companion object {
        const val TAG = "OnDeviceInference"
        const val NANOS_PER_MILLI = 1_000_000L

        fun elapsedMsSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLI
    }
}
