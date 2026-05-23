package com.agarthavision

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.ui.components.MicroscopyViewport
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            AgarthaVisionTheme {

                val context = LocalContext.current

                val cameraManager = remember {
                    CameraManager(context)
                }

                var imageCapture by remember {
                    mutableStateOf<ImageCapture?>(null)
                }

                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { _ -> }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }

                MicroscopyViewport(
                    cameraManager = cameraManager,
                    onReady = {
                        imageCapture = it
                    }
                )
            }
        }
    }
}