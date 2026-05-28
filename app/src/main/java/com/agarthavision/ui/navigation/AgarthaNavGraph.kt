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
import com.agarthavision.ui.dashboard.DashboardScreen
import com.agarthavision.ui.records.RecordsScreen
import com.agarthavision.ui.records.SampleDetailScreen
import com.agarthavision.ui.records.SessionDetailScreen
import com.agarthavision.ui.sessions.SessionsScreen
import com.agarthavision.ui.settings.SettingsScreenPlaceholder
import com.agarthavision.ui.verify.VerificationQueueScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Sessions : Screen("sessions")
    data object Capture : Screen("capture")
    data object Records : Screen("records")
    data object SessionDetail : Screen("records/session/{sessionId}") {
        fun createRoute(sessionId: String) = "records/session/$sessionId"
    }
    data object SampleDetail : Screen("records/sample/{sampleId}") {
        fun createRoute(sampleId: String) = "records/sample/$sampleId"
    }
    data object VerificationQueue : Screen("verification_queue")
    data object Settings : Screen("settings")
}

/**
 * Root nav graph. Starts on [Screen.Login]; LoginViewModel will redirect to
 * [Screen.Sessions] on cold start when a persisted Supabase session exists.
 * The picker is the only path into [Screen.Capture]. See ADR-005 and
 * docs/03_MOBILE_APP_PLAN.md §1.0.
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
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.Sessions.route) {
            SessionsScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSessionSelected = {
                    navController.navigate(Screen.Capture.route) {
                        popUpTo(Screen.Sessions.route) { inclusive = false }
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
                onReportsClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId)) {
                        launchSingleTop = true
                    }
                },
                onVerifyQueueClick = {
                    navController.navigate(Screen.VerificationQueue.route) {
                        launchSingleTop = true
                    }
                },
                onSessionEnded = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Sessions.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.VerificationQueue.route) {
            VerificationQueueScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.Records.route) {
            RecordsScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
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
