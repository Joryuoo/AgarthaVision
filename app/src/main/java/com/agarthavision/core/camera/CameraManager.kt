package com.agarthavision.core.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds the device camera for **continuous frame analysis** (no shutter).
 *
 * Per docs/03_MOBILE_APP_PLAN.md §1.2 the Phase 1 capture flow uses CameraX's
 * [ImageAnalysis] — frames flow into the supplied [ImageAnalysis.Analyzer], which
 * the `FrameSampler` throttles to one frame every two seconds and dispatches to
 * `InferFrameUseCase`. There is no `ImageCapture` use case in Phase 1.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Binds the [Preview] + [ImageAnalysis] use cases to [lifecycleOwner].
     *
     * @param analyzerExecutor where the [analyzer] runs — usually
     *   `Dispatchers.IO.asExecutor()` or the main executor for low-latency UI overlays.
     * @return the bound [Camera] so callers can adjust torch / zoom if needed.
     */
    suspend fun bindAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer,
        analyzerExecutor: Executor = ContextCompat.getMainExecutor(context),
    ): Camera = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                @Suppress("DEPRECATION") // setTargetResolution — Roboflow input dims
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(INFERENCE_INPUT_SIZE_PX, INFERENCE_INPUT_SIZE_PX))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply { setAnalyzer(analyzerExecutor, analyzer) }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                continuation.resume(camera)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private companion object {
        private const val INFERENCE_INPUT_SIZE_PX = 640
    }
}
