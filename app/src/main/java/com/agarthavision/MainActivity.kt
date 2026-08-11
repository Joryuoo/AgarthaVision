package com.agarthavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.ui.navigation.AgarthaNavGraph
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App entry point. Hosts the [AgarthaNavGraph] inside the theme driven by the
 * persisted light/dark preference.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var frameSampler: FrameSampler

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            AgarthaVisionTheme(darkTheme = themeMode == ThemeMode.DARK) {
                AgarthaNavGraph(
                    cameraManager = cameraManager,
                    frameSampler = frameSampler
                )
            }
        }
    }
}
