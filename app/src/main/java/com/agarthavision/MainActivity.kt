package com.agarthavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.AuthGate
import com.agarthavision.ui.navigation.Screen
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
        // Must run before super.onCreate() so the system swaps the launch theme
        // (Theme.AgarthaVision.Starting) for the splash and hands off to the app theme.
        val splash = installSplashScreen()

        // Hold the splash until the first-run gate resolves. Reading the cached identity is
        // a fast disk read, but it is not instant, and without this the Dashboard composes
        // for a frame or two behind the login screen on a fresh install — which reads as a
        // flash of someone else's data. This gate is DataStore-only: resolving it never
        // constructs the Supabase client or the session graph on this path (86d4byw6p).
        splash.setKeepOnScreenCondition { mainViewModel.authGate.value == AuthGate.Loading }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val authGate by mainViewModel.authGate.collectAsStateWithLifecycle()
            AgarthaVisionTheme(darkTheme = themeMode == ThemeMode.DARK) {
                // Loading never reaches composition: the splash is still up. Rendering the
                // Dashboard for it would defeat the condition above.
                if (authGate != AuthGate.Loading) {
                    AgarthaNavGraph(
                        cameraManager = cameraManager,
                        frameSampler = frameSampler,
                        startDestination = if (authGate == AuthGate.NeedsLogin) {
                            Screen.Login.route
                        } else {
                            Screen.Dashboard.route
                        },
                    )
                }
            }
        }
    }
}
