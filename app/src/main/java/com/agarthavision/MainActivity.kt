package com.agarthavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.ui.navigation.AgarthaNavGraph
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App entry point. Hosts the [AgarthaNavGraph].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var frameSampler: FrameSampler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgarthaVisionTheme {
                AgarthaNavGraph(
                    cameraManager = cameraManager,
                    frameSampler = frameSampler
                )
            }
        }
    }
}
