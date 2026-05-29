package com.agarthavision.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.ui.capture.CaptureScreen
import com.agarthavision.ui.components.AgarthaBottomBar
import com.agarthavision.ui.components.bottomBarRoutes
import com.agarthavision.ui.dashboard.DashboardScreen
import com.agarthavision.ui.login.LoginScreen
import com.agarthavision.ui.records.AppColors
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

@Composable
fun AgarthaNavGraph(
    cameraManager: CameraManager,
    frameSampler: FrameSampler
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val showBottomBar = currentRoute != null && currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(180))
            ) {
                AgarthaBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            // Pop back to start so each tab maintains its own stack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    verifyQueueCount = 0 // In a real app, wire this to a ViewModel that observes the queue globally
                )
            }
        },
        containerColor = AppColors.White
    ) { inner ->
        AgarthaNavHost(
            navController = navController,
            cameraManager = cameraManager,
            frameSampler = frameSampler,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        )
    }
}

@Composable
fun AgarthaNavHost(
    navController: NavHostController,
    cameraManager: CameraManager,
    frameSampler: FrameSampler,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier,
        // Default for unspecified destinations: fade
        enterTransition    = { fadeIn(tween(220)) },
        exitTransition     = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition  = { fadeOut(tween(180)) }
    ) {
        // Login → Dashboard
        composable(Screen.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // === Primary tabs (fade between them) ===
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Sessions.route) {
            SessionsScreen(
                onNavigate = { route -> navController.navigate(route) },
                onSessionSelected = {
                    navController.navigate(Screen.Capture.route)
                }
            )
        }
        composable(Screen.Records.route) {
            RecordsScreen(
                onNavigate = { route -> navController.navigate(route) },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }

        // === Drill-downs from Sessions (slide horizontal) ===
        composable(
            route = Screen.Capture.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(220)
                ) + fadeOut(tween(180))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(220)
                ) + fadeOut(tween(180))
            }
        ) {
            CaptureScreen(
                cameraManager = cameraManager,
                frameSampler = frameSampler,
                onRecordsClick = { navController.navigate(Screen.Records.route) },
                onReportsClick = { sessionId -> navController.navigate(Screen.SessionDetail.createRoute(sessionId)) },
                onVerifyQueueClick = { navController.navigate(Screen.VerificationQueue.route) },
                onSessionEnded = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Sessions.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.VerificationQueue.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220)
                )
            }
        ) {
            VerificationQueueScreen(
                onBackClick = { navController.popBackStack() },
                onSampleDetailClick = { sampleId ->
                    navController.navigate(Screen.SampleDetail.createRoute(sampleId))
                }
            )
        }

        // === Drill-downs from Records (slide horizontal) ===
        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220)
                )
            }
        ) {
            SessionDetailScreen(
                onBack = { navController.popBackStack() },
                onSampleClick = { sampleId ->
                    navController.navigate(Screen.SampleDetail.createRoute(sampleId))
                }
            )
        }

        composable(
            route = Screen.SampleDetail.route,
            arguments = listOf(navArgument("sampleId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220)
                )
            }
        ) {
            SampleDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) { SettingsScreenPlaceholder() }
    }
}
