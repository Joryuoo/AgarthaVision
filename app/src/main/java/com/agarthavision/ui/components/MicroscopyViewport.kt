package com.agarthavision.ui.components

import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.agarthavision.core.camera.CameraManager

/**
 * Camera preview surface for the capture flow.
 *
 * Binds [CameraManager.bindAnalysis] to the lifecycle and routes frames into the
 * supplied [analyzer] (typically the `FrameSampler` injected into the
 * `CaptureViewModel`). See docs/03_MOBILE_APP_PLAN.md §1.2 and §1.3.
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
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraState = remember { mutableStateOf<Camera?>(null) }

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
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
