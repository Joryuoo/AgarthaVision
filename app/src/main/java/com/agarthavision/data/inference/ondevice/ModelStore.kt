package com.agarthavision.data.inference.ondevice

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device models bundled in `assets/models/`, each a `<model_version>.tflite` with a
 * `<model_version>.json` manifest beside it.
 *
 * Both precisions are bundled so they can be measured on the same phone. [SHIPPED] is the one
 * the app runs.
 */
object OnDeviceModels {
    const val FP16 = "yolo12n-effv2s-v1-tflite-fp16"
    const val INT8 = "yolo12n-effv2s-v1-tflite-int8"

    /**
     * The build production inference uses. fp16 until the on-phone benchmark shows int8 keeps
     * parity and is meaningfully faster.
     */
    const val SHIPPED = FP16
}

/** A compiled model ready to run, and what it was compiled for. */
class LoadedModel(
    val model: CompiledModel,
    val manifest: ModelManifest,
    /** Which accelerators LiteRT was asked for, for the latency log. */
    val accelerators: String,
    val loadMs: Long,
)

/**
 * Finds a bundled model and compiles it.
 *
 * Nothing here throws: a missing asset, a malformed manifest or a model LiteRT refuses all come
 * back as null with the reason logged, so the engine can report itself unavailable instead of
 * failing a capture.
 */
@Singleton
class ModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    fun load(modelName: String): LoadedModel? {
        val manifest = readManifest(modelName) ?: return null
        val assetPath = "$ASSET_DIR/${manifest.modelFile}"
        return if (hasAsset(assetPath)) {
            compile(assetPath, manifest, GPU_WITH_CPU_FALLBACK) ?: compile(assetPath, manifest, CPU_ONLY)
        } else {
            Log.w(TAG, "Manifest for $modelName names $assetPath, which is not bundled.")
            null
        }
    }

    private fun readManifest(modelName: String): ModelManifest? {
        val path = "$ASSET_DIR/$modelName.json"
        val manifest = runCatching {
            val json = context.assets.open(path).bufferedReader().use { it.readText() }
            gson.fromJson(json, ModelManifest::class.java)
        }.onFailure { Log.w(TAG, "No readable manifest at assets/$path.", it) }.getOrNull()

        if (manifest != null && !manifest.isUsable) {
            Log.w(TAG, "Manifest at assets/$path is incomplete or inconsistent: $manifest")
        }
        return manifest?.takeIf { it.isUsable }
    }

    /**
     * GPU first, with LiteRT placing any op the GPU cannot run on the CPU. A device whose GPU
     * accelerator will not initialise at all gets a CPU-only compile instead of no model.
     */
    private fun compile(assetPath: String, manifest: ModelManifest, accelerators: Set<Accelerator>): LoadedModel? {
        val startedAt = System.nanoTime()
        return runCatching {
            CompiledModel.create(context.assets, assetPath, CompiledModel.Options(accelerators))
        }.onFailure {
            Log.w(TAG, "Compiling ${manifest.modelVersion} for $accelerators failed.", it)
        }.getOrNull()?.let { model ->
            LoadedModel(
                model = model,
                manifest = manifest,
                accelerators = accelerators.joinToString("+"),
                loadMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI,
            )
        }
    }

    private fun hasAsset(path: String): Boolean = runCatching {
        context.assets.openFd(path).close()
        true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "ModelStore"
        const val ASSET_DIR = "models"
        const val NANOS_PER_MILLI = 1_000_000L
        val GPU_WITH_CPU_FALLBACK = setOf(Accelerator.GPU, Accelerator.CPU)
        val CPU_ONLY = setOf(Accelerator.CPU)
    }
}
