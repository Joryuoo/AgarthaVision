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
import com.agarthavision.domain.usecase.capture.CaptureSampleUseCase
import com.agarthavision.ui.components.MicroscopyScreen
import com.agarthavision.ui.components.MicroscopyViewport
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var captureSampleUseCase: CaptureSampleUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            AgarthaVisionTheme {

                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { _ -> }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }

                MicroscopyScreen(
                    cameraManager = cameraManager,
                    captureSampleUseCase = captureSampleUseCase
                )
            }
        }
    }
}
