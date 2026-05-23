package com.agarthavision.ui.components

import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.domain.usecase.capture.CaptureSampleUseCase
import java.io.File
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
import com.agarthavision.ui.theme.AgarthaVisionTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.komoui.themes.KomoTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import com.komoui.components.sooner.SonnerHost
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

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
    captureSampleUseCase: CaptureSampleUseCase,
    onCapture: (ImageCapture) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var latestCapture by remember { mutableStateOf<File?>(null) }

    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        scope.launch {
            if (fineGranted || coarseGranted) {
                snackbarHostState.showSnackbar("Location permission granted")
            } else {
                snackbarHostState.showSnackbar("Location denied. Sample will be saved without GPS.")
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(bottom = 12.dp)
            ) {
                MicroscopyViewport(
                    cameraManager = cameraManager,
                    modifier = Modifier.fillMaxSize(),
                    onReady = {
                        activeImageCapture = it
                        onCapture(it)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Left side spacer (or empty)
                Spacer(modifier = Modifier.size(72.dp))

                // Center shutter button
                ShutterButton(
                    onClick = {
                        if (!hasLocationPermission()) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }

                        activeImageCapture?.let { capture ->
                            scope.launch {
                                try {
                                    val result = captureSampleUseCase(capture)
                                    result.onSuccess { sample ->
                                        val file = File(sample.filePath)
                                        latestCapture = file
                                        snackbarHostState.showSnackbar("Sample saved with metadata: ${sample.id}")
                                    }.onFailure { e ->
                                        snackbarHostState.showSnackbar("Capture failed: ${e.localizedMessage}")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Capture failed: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                )

                // Right side gallery/thumbnail button
                IconButton(
                    onClick = { /* TODO: open gallery */ },
                    modifier = Modifier.size(72.dp)
                ) {
                    if (latestCapture != null) {
                        AsyncImage(
                            model = latestCapture,
                            contentDescription = "Latest capture",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Black
                        )
                    }
                }
            }
        }

        SonnerHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }
}