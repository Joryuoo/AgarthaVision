package com.agarthavision

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.agarthavision.ui.components.BiometricLockOverlay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.agarthavision.core.auth.BiometricPromptManager
import com.agarthavision.core.auth.BiometricResult
import com.agarthavision.core.auth.BiometricStatus
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.AuthGate
import com.agarthavision.ui.navigation.AgarthaNavGraph
import com.agarthavision.ui.navigation.onboardingExitRoute
import com.agarthavision.ui.navigation.startRouteFor
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App entry point. Hosts the [AgarthaNavGraph] inside the theme driven by the
 * persisted light/dark preference.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var frameSampler: FrameSampler
    @Inject lateinit var biometricPromptManager: BiometricPromptManager

    private val mainViewModel: MainViewModel by viewModels()
    private var isPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the system swaps the launch theme
        // (Theme.AgarthaVision.Starting) for the splash and hands off to the app theme.
        val splash = installSplashScreen()

        // Hold the splash until the first-run gate resolves. Reading the cached identity is
        // a fast disk read, but it is not instant, and without this the Dashboard composes
        // for a frame or two behind the login screen on a fresh install — which reads as a
        // flash of someone else's data. This gate is DataStore-only: resolving it never
        // constructs the Supabase client or the session graph on this path (86d4byw6p).
        splash.setKeepOnScreenCondition {
            mainViewModel.authGate.value == AuthGate.Loading || mainViewModel.hasSeenOnboarding.value == null
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val authGate by mainViewModel.authGate.collectAsStateWithLifecycle()
            val hasSeenOnboarding by mainViewModel.hasSeenOnboarding.collectAsStateWithLifecycle()
            val signedOutByServer by mainViewModel.signedOutByServer.collectAsStateWithLifecycle()
            val isBiometricLockEnabled by mainViewModel.isBiometricLockEnabled.collectAsStateWithLifecycle()
            val isLocked by mainViewModel.isLocked.collectAsStateWithLifecycle()

            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val showLockOverlay = isBiometricLockEnabled && authGate == AuthGate.Authed && isLocked

            androidx.compose.runtime.LaunchedEffect(showLockOverlay) {
                if (showLockOverlay) {
                    triggerBiometricPrompt()
                }
            }

            AgarthaVisionTheme(darkTheme = isDark) {
                // Loading never reaches composition: the splash is still up. Rendering the
                // Dashboard for it would defeat the condition above.
                if (authGate != AuthGate.Loading && hasSeenOnboarding != null) {
                    // Fixed for the life of the graph: finishing onboarding flips
                    // hasSeenOnboarding, which must not rebuild the NavHost.
                    val startRoute = rememberSaveable { startRouteFor(authGate, hasSeenOnboarding == true) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AgarthaNavGraph(
                            cameraManager = cameraManager,
                            frameSampler = frameSampler,
                            startDestination = startRoute,
                            signedOutByServer = signedOutByServer,
                            onboardingExitRoute = onboardingExitRoute(authGate),
                            biometricPromptManager = biometricPromptManager,
                        )

                        if (showLockOverlay) {
                            BiometricLockOverlay(
                                onUnlockClick = { triggerBiometricPrompt() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerBiometricPrompt() {
        if (isPromptShowing) return
        val status = biometricPromptManager.getBiometricStatus()
        if (status == BiometricStatus.READY) {
            isPromptShowing = true
            biometricPromptManager.showBiometricPrompt(
                activity = this,
                title = getString(R.string.biometric_prompt_title),
                subtitle = getString(R.string.biometric_prompt_subtitle),
                negativeButtonText = getString(R.string.biometric_prompt_cancel),
            ) { result ->
                isPromptShowing = false
                if (result is BiometricResult.Success) {
                    mainViewModel.unlockApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (mainViewModel.isBiometricLockEnabled.value) {
            mainViewModel.lockApp()
        }
    }
}
