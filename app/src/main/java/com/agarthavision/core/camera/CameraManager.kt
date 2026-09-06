package com.agarthavision.core.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds the device camera for **continuous frame analysis** (no shutter).
 *
 * Per CONTEXT.md
 * [ImageAnalysis] — frames flow into the supplied [ImageAnalysis.Analyzer], which
 * the `FrameSampler` throttles to one frame every two seconds and dispatches to
 * `InferFrameUseCase`. There is no `ImageCapture` use case in Phase 1.
 *
 * Both use cases are pinned to the same 4:3 aspect-ratio strategy so they share one
 * field of view. That is what lets the capture screen draw an honest boundary: what
 * the preview shows is what the analyzer receives, and the frame posted for
 * inference is the centred square of it (see `ImageProxy.toJpegBytes`).
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _previewAspectRatio = MutableStateFlow<Float?>(null)

    /**
     * Width / height of the bound preview stream **as displayed**, or null before the
     * first successful bind. The capture boundary overlay needs this to work out where
     * `PreviewView`'s letterboxed image actually sits inside the view.
     */
    val previewAspectRatio: StateFlow<Float?> = _previewAspectRatio.asStateFlow()

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

                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(FOUR_BY_THREE)
                            .build(),
                    )
                    .build()
                    .apply { surfaceProvider = previewView.surfaceProvider }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(FOUR_BY_THREE)
                            // CLOSEST_HIGHER_THEN_LOWER prefers a stream at or above the
                            // model input size, so toJpegBytes only ever downscales.
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(CAPTURE_FRAME_SIZE_PX, CAPTURE_FRAME_SIZE_PX),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
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
                _previewAspectRatio.value = preview.resolutionInfo?.displayAspectRatio()
                continuation.resume(camera)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Aspect ratio of this stream once its rotation is applied. A 640x480 buffer
     * delivered with a 90-degree rotation is displayed 480x640, so the ratio flips.
     */
    private fun androidx.camera.core.ResolutionInfo.displayAspectRatio(): Float {
        val swapped = rotationDegrees == QUARTER_TURN || rotationDegrees == THREE_QUARTER_TURN
        val width = if (swapped) resolution.height else resolution.width
        val height = if (swapped) resolution.width else resolution.height
        return if (height == 0) 1f else width.toFloat() / height.toFloat()
    }

    private companion object {
        private val FOUR_BY_THREE = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        /** Rotations that swap a stream's width and height when displayed. */
        private const val QUARTER_TURN = 90
        private const val THREE_QUARTER_TURN = 270
    }
}
