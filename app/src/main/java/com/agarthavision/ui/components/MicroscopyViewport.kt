package com.agarthavision.ui.components

import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.ui.theme.AgarthaVisionTheme

// Box + Canvas + Image — Capture screen and detection card.
// Displays a raw microscopy frame; slot for DetectionOverlay drawn on top.
// See docs/components.md §6.
@Composable
fun MicroscopyViewport(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier,
    onReady: (ImageCapture) -> Unit = {},
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {

        imageCapture = cameraManager.bindPreview(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
        )

        imageCapture?.let(onReady)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.Black)
    ) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MicroscopyViewportPreview() {
    AgarthaVisionTheme {

        Box(
            modifier = Modifier.background(Color.Black)
        ) {
            MicroscopyViewportPlaceholder()
        }
    }
}

@Composable
private fun MicroscopyViewportPlaceholder() {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.DarkGray)
    )
}