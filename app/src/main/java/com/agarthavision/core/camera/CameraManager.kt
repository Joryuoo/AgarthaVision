package com.agarthavision.core.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ): ImageCapture = suspendCancellableCoroutine { continuation ->

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                )
                .build()

            try {

                cameraProvider?.unbindAll()

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )

                continuation.resume(imageCapture)

            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun captureImage(
        imageCapture: ImageCapture,
    ): File = suspendCancellableCoroutine { continuation ->

        val capturesDir = File(
            context.filesDir,
            "captures"
        ).apply {
            mkdirs()
        }

        val photoFile = File(
            capturesDir,
            "${UUID.randomUUID()}.jpg"
        )

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile)
                .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    continuation.resume(photoFile)
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
}