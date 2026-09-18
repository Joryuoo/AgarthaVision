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
import androidx.compose.foundation.layout.WindowInsets
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
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.records.RecordsScreen
import com.agarthavision.ui.records.SampleDetailScreen
import com.agarthavision.ui.records.SessionDetailScreen
import com.agarthavision.ui.patients.PatientFormScreen
import com.agarthavision.ui.patients.PatientsScreen
import com.agarthavision.ui.sessions.SessionsScreen
import com.agarthavision.ui.settings.SettingsScreen
import com.agarthavision.ui.verify.VerificationQueueScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Patients : Screen("patients")

    /**
     * One patient's session list. PB-09c builds what it shows; today it is the sessions
     * list, unscoped.
     */
    data object PatientSessions : Screen("patients/{patientId}") {
        fun createRoute(patientId: String) = "patients/$patientId"
    }

    /**
     * The New / Edit Patient form. Omitting `patientId` means a blank form.
     *
     * Deliberately **not** under `patients/`. A literal `patients/form` would also match
     * [PatientSessions]'s `patients/{patientId}` pattern, and which one wins is a matter
     * of registration order rather than intent — the kind of ambiguity that resolves
     * correctly in testing and wrongly after an unrelated reorder.
     */
    data object PatientForm : Screen("patient-form?patientId={patientId}") {
        fun createRoute(patientId: String? = null) =
            if (patientId == null) "patient-form" else "patient-form?patientId=$patientId"
    }

    data object Capture : Screen("capture")

    /**
     * The Reports tab. Renamed from `records` with the tab itself: the Records *screen*
     * becomes session-scoped in PB-19, and two things called Records would confuse
     * everyone. What this tab lists is PB-22; today it still shows [RecordsScreen].
     *
     * The `records/...` drill-down routes below are a separate namespace and keep their
     * spelling — they address a session or a sample, not the tab.
     */
    data object Reports : Screen("reports")
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
    frameSampler: FrameSampler,
    startDestination: String = Screen.Dashboard.route
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
                    onTabSelected = { tab -> navController.navigateToTab(tab.route) },
                )
            }
        },
        containerColor = AgarthaTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { inner ->
        AgarthaNavHost(
            navController = navController,
            cameraManager = cameraManager,
            frameSampler = frameSampler,
            startDestination = startDestination,
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
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Default for unspecified destinations: fade
        enterTransition    = { fadeIn(tween(220)) },
        exitTransition     = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition  = { fadeOut(tween(180)) }
    ) {
        // Login is the start destination on first run and cannot be dismissed: a Patient
        // must belong to a User and a Session to a Patient, so there is nothing to attach a
        // patient to until somebody has signed in. It supersedes ADR-007's pop-back
        // behaviour, where login was an optional detour entered from the Dashboard banner.
        //
        // The gate is first-run only. Once an identity is cached it is satisfied forever,
        // including offline and after the Supabase token expires — which is what keeps the
        // rest of the app offline-first. See ResolveAuthGateUseCase.
        //
        // popUpTo(inclusive) rather than popBackStack(): entered as the start destination
        // there is nothing behind it to pop to, and back must not return here afterwards.
        composable(Screen.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }

        // === Primary tabs (fade between them) ===
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Patients.route) {
            PatientsScreen(
                onPatientSelected = { patientId ->
                    navController.navigate(Screen.PatientSessions.createRoute(patientId))
                },
                onEditPatient = { patientId ->
                    navController.navigate(Screen.PatientForm.createRoute(patientId))
                },
                onCreatePatient = {
                    navController.navigate(Screen.PatientForm.createRoute())
                },
            )
        }

        composable(
            route = Screen.PatientForm.route,
            arguments = listOf(
                navArgument("patientId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PatientFormScreen(onDone = { navController.popBackStack() })
        }

        // One patient's session list, scoped by the `patientId` path argument the
        // SessionsViewModel reads off SavedStateHandle. Every row opens Capture; Session
        // Detail is reached from Records instead, so no callback for it is passed here.
        composable(Screen.PatientSessions.route) {
            SessionsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                onNavigateToCapture = {
                    navController.navigate(Screen.Capture.route)
                },
            )
        }
        composable(Screen.Reports.route) {
            RecordsScreen(
                onNavigate = { route -> navController.navigate(route) },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }

        // === Drill-downs from Patients (slide horizontal) ===
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
                onReportsClick = { sessionId -> navController.navigate(Screen.SessionDetail.createRoute(sessionId)) },
                onVerifyQueueClick = { navController.navigate(Screen.VerificationQueue.route) },
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
                },
                onGoToRecords = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // === Drill-downs from Reports (slide horizontal) ===
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
                },
                onOpenVerifyQueue = {
                    navController.navigate(Screen.VerificationQueue.route) {
                        popUpTo(Screen.VerificationQueue.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                onSignInClick = { navController.navigate(Screen.Login.route) },
            )
        }
    }
}

/**
 * Navigates to one of the four bottom-bar tabs, preserving each tab's own back stack.
 *
 * **Use this for every navigation whose destination is a tab route, not just the bar taps.**
 * Mixing this multi-back-stack pattern with an ad-hoc `popUpTo(someRoute)` elsewhere in the
 * same graph is a known Navigation-Compose footgun, and it has bitten this app once: ending a
 * session used to navigate to the sessions tab with `popUpTo` on its route, which is a no-op
 * when that tab is only *saved* rather than present, so a second entry was pushed alongside
 * the saved one and the Home tab stopped responding (86d4ad75y).
 *
 * That path is gone - sessions no longer end - but the hazard is structural, so the convention
 * has a name here rather than being copied by hand at each call site. A destination that is not
 * a tab (capture, a detail screen, login) is an ordinary `navigate` and must not use this:
 * restoring saved state is exactly wrong for a screen you are pushing onto the current stack.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // Pop back to start so each tab maintains its own stack.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
