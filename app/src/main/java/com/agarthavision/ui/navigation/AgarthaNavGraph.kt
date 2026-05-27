package com.agarthavision.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.ui.capture.CaptureScreen
import com.agarthavision.ui.login.LoginScreen
import com.agarthavision.ui.records.RecordsScreen
import com.agarthavision.ui.records.SampleDetailScreen
import com.agarthavision.ui.records.SessionDetailScreen
import com.agarthavision.ui.settings.SettingsScreenPlaceholder

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Capture : Screen("capture")
    data object Records : Screen("records")
    data object SessionDetail : Screen("records/session/{sessionId}") {
        fun createRoute(sessionId: String) = "records/session/$sessionId"
    }
    data object SampleDetail : Screen("records/sample/{sampleId}") {
        fun createRoute(sampleId: String) = "records/sample/$sampleId"
    }
    data object Settings : Screen("settings")
}

/**
 * Root nav graph. Starts on [Screen.Login]; LoginViewModel will redirect to
 * [Screen.Capture] on cold start when a persisted Supabase session exists.
 * See docs/03_MOBILE_APP_PLAN.md §1.0.
 */
@Composable
fun AgarthaNavGraph(
    cameraManager: CameraManager,
    frameSampler: FrameSampler,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.Capture.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.Capture.route) {
            CaptureScreen(
                cameraManager = cameraManager,
                frameSampler = frameSampler,
                onRecordsClick = {
                    navController.navigate(Screen.Records.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.Records.route) {
            RecordsScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.SessionDetail.route) {
            SessionDetailScreen(
                onBack = { navController.popBackStack() },
                onSampleClick = { sampleId ->
                    navController.navigate(Screen.SampleDetail.createRoute(sampleId))
                },
            )
        }
        composable(Screen.SampleDetail.route) {
            SampleDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) { SettingsScreenPlaceholder() }
    }
}
