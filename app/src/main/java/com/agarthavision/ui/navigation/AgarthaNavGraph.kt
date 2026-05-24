package com.agarthavision.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agarthavision.ui.capture.CaptureScreenPlaceholder
import com.agarthavision.ui.login.LoginScreenPlaceholder
import com.agarthavision.ui.records.RecordsScreenPlaceholder
import com.agarthavision.ui.settings.SettingsScreenPlaceholder

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Capture : Screen("capture")
    data object Records : Screen("records")
    data object Settings : Screen("settings")
}

/**
 * Root nav graph. Starts on [Screen.Login]; LoginViewModel will redirect to
 * [Screen.Capture] on cold start when a persisted Supabase session exists.
 * See docs/03_MOBILE_APP_PLAN.md §1.0.
 */
@Composable
fun AgarthaNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
    ) {
        composable(Screen.Login.route) { LoginScreenPlaceholder() }
        composable(Screen.Capture.route) { CaptureScreenPlaceholder() }
        composable(Screen.Records.route) { RecordsScreenPlaceholder() }
        composable(Screen.Settings.route) { SettingsScreenPlaceholder() }
    }
}
