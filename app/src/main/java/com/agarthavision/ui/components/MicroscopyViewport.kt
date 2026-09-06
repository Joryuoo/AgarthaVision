package com.agarthavision.ui.components

import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.ui.theme.AppColors

/**
 * Camera preview surface for the capture flow.
 *
 * Binds [CameraManager.bindAnalysis] to the lifecycle and routes frames into the
 * supplied [analyzer] (typically the `FrameSampler` injected into the
 * `CaptureViewModel`). See CONTEXT.md.
 *
 * The preview uses `FIT_CENTER`, not `FILL_CENTER`: the analysed stream is wider
 * than a phone screen, so filling would push part of what the model sees off the
 * display and make [CaptureFrameBoundary] a lie. Fitting letterboxes against the
 * dark backdrop instead, and every pixel the analyzer receives is on screen.
 */
@Composable
fun MicroscopyViewport(
    cameraManager: CameraManager,
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
    onCameraReady: (Camera) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    val cameraState = remember { mutableStateOf<Camera?>(null) }
    val previewAspectRatio by cameraManager.previewAspectRatio.collectAsStateWithLifecycle()

    LaunchedEffect(cameraManager, analyzer) {
        val camera = cameraManager.bindAnalysis(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = analyzer,
        )
        cameraState.value = camera
        onCameraReady(camera)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Capture is dark tool mode — dark backdrop while the camera warms up,
            // and the letterbox bars either side of the fitted preview.
            .background(AppColors.Gray900),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        CaptureFrameBoundary(
            previewAspectRatio = previewAspectRatio,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
