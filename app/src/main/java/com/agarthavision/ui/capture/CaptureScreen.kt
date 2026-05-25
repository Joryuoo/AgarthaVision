package com.agarthavision.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.ui.components.MicroscopyViewport
import com.komoui.components.sooner.SonnerHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Full-screen camera preview with continuous inference controls.
 */
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = hiltViewModel(),
    cameraManager: CameraManager, // We'll pass these from NavGraph or inject
    frameSampler: FrameSampler,
) {
    val state by viewModel.state.collectAsState()
    val sonnerHostState = remember { SnackbarHostState() }

    // Toast for new detections
    LaunchedEffect(viewModel) {
        viewModel.state
            .map { it.flaggedFrames.size }
            .distinctUntilChanged()
            .collect { size ->
                if (size > 0) {
                    val latest = viewModel.state.value.flaggedFrames.first()
                    val eggType = latest.predictions.firstOrNull()?.classLabel ?: "egg"
                    val confidence = latest.predictions.firstOrNull()?.confidence ?: 0f
                    sonnerHostState.showSnackbar(
                        message = "$eggType detected · ${"%.2f".format(confidence)} · [view]",
                        withDismissAction = true
                    )
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Camera Viewport (80% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f)
            ) {
                MicroscopyViewport(
                    cameraManager = cameraManager,
                    analyzer = frameSampler,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (state.isRecording) {
                    Text(
                        text = "REC",
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Session Controls (20% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.2f),
                contentAlignment = Alignment.Center
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            if (state.isRecording) viewModel.stopRecording()
                            else viewModel.startRecording()
                        }
                    ) {
                        Text(if (state.isRecording) "Stop Recording" else "Start Recording")
                    }
                }
                
                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            }
        }

        SonnerHost(hostState = sonnerHostState)
    }
}
