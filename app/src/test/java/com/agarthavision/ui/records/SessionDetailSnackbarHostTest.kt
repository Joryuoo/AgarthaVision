package com.agarthavision.ui.records

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Keeps Session Detail's snackbar above the system navigation bar (14zcqnthuac).
 *
 * The screen zeroes its Scaffold's `contentWindowInsets`, which also zeroes the snackbar's
 * offset, so the Share action after generating a report drew behind the system buttons. This
 * lays [SessionDetailSnackbarHost] out in the same Scaffold configuration, dispatches a
 * navigation-bar inset, and checks the snackbar's bottom edge clears it.
 *
 * The control case uses a bare [SnackbarHost]. It proves the inset actually reached Compose, so
 * the real case cannot pass just because nothing was dispatched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SessionDetailSnackbarHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the snackbar clears the navigation bar`() {
        render { SessionDetailSnackbarHost(it) }

        val gap = gapBelowSnackbar()
        assertTrue("Snackbar sits $gap above the bottom edge.", gap >= NAV_BAR_HEIGHT)
    }

    @Test
    fun `control - without the padding the snackbar sits behind the navigation bar`() {
        render { SnackbarHost(it) }

        val gap = gapBelowSnackbar()
        assertTrue("Snackbar sits $gap above the bottom edge.", gap < NAV_BAR_HEIGHT)
    }

    private fun render(host: @Composable (SnackbarHostState) -> Unit) {
        composeRule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
        }
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                hostState.showSnackbar(
                    message = MESSAGE,
                    actionLabel = "Share",
                    duration = SnackbarDuration.Indefinite,
                )
            }
            AgarthaVisionTheme {
                // The screen's own configuration: no bottom bar, zeroed content insets.
                Scaffold(
                    snackbarHost = { host(hostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { }
            }
        }
        composeRule.runOnUiThread {
            val navBarPx = (NAV_BAR_HEIGHT.value * composeRule.activity.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, insets)
        }
        composeRule.waitForIdle()
    }

    private fun gapBelowSnackbar(): Dp {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val snackbar = composeRule.onNodeWithText(MESSAGE).getUnclippedBoundsInRoot()
        return root.bottom - snackbar.bottom
    }

    private companion object {
        const val MESSAGE = "Report generated"
        val NAV_BAR_HEIGHT = 48.dp
    }
}
