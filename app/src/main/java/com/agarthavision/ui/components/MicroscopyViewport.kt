package com.agarthavision.ui.components

import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun MicroscopyScreen(
    cameraManager: CameraManager,
    onCapture: (ImageCapture) -> Unit = {},
    onCaptureClick: () -> Unit = {}
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(bottom = 24.dp)
        ) {
            MicroscopyViewport(
                cameraManager = cameraManager,
                modifier = Modifier.fillMaxSize(),
                onReady = onCapture
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Left side spacer (or empty)
            Spacer(modifier = Modifier.size(72.dp))

            // Center shutter button
            ShutterButton(
                onClick = onCaptureClick
            )

            // Right side gallery button
            IconButton(
                onClick = { /* TODO: open gallery */ },
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}