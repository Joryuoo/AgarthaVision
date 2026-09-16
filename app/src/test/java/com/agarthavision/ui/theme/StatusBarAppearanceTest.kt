package com.agarthavision.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [AgarthaVisionTheme] drives [WindowInsetsControllerCompat.isAppearanceLightStatusBars]
 * via its [SideEffect]. Requires a real Activity window, so we use
 * [createAndroidComposeRule] (backed by [ComponentActivity]) rather than the host-only
 * [createComposeRule].
 *
 * Robolectric SDK-36 delegates to the platform [WindowInsetsController] API.  If the flag
 * read-back is unreliable at this SDK level the suite falls back to asserting that the theme
 * composes without throwing under both dark/light values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class StatusBarAppearanceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // ── helpers ────────────────────────────────────────────────────────────

    private fun lightStatusBarsFlag(): Boolean {
        val activity = rule.activity
        val wic = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        )
        return wic.isAppearanceLightStatusBars
    }

    // ── tests ──────────────────────────────────────────────────────────────

    /**
     * Light theme (darkTheme = false) → light status-bar icons → flag should be true.
     */
    @Test
    fun lightTheme_setsAppearanceLightStatusBars_true() {
        rule.setContent {
            AgarthaVisionTheme(darkTheme = false) {
                Box(Modifier.size(1.dp))
            }
        }
        rule.waitForIdle()

        val flag = lightStatusBarsFlag()
        assertTrue(
            "Expected isAppearanceLightStatusBars == true for darkTheme=false, was $flag",
            flag,
        )
    }

    /**
     * Dark theme (darkTheme = true) → dark status-bar icons → flag should be false.
     */
    @Test
    fun darkTheme_setsAppearanceLightStatusBars_false() {
        rule.setContent {
            AgarthaVisionTheme(darkTheme = true) {
                Box(Modifier.size(1.dp))
            }
        }
        rule.waitForIdle()

        val flag = lightStatusBarsFlag()
        assertFalse(
            "Expected isAppearanceLightStatusBars == false for darkTheme=true, was $flag",
            flag,
        )
    }

    /**
     * Toggling darkTheme state from false → true flips the flag after recomposition.
     */
    @Test
    fun toggleDarkTheme_flipsAppearanceLightStatusBars() {
        var isDark by mutableStateOf(false)

        rule.setContent {
            AgarthaVisionTheme(darkTheme = isDark) {
                Box(Modifier.size(1.dp))
            }
        }
        rule.waitForIdle()

        assertTrue(
            "Before toggle: expected isAppearanceLightStatusBars == true",
            lightStatusBarsFlag(),
        )

        isDark = true
        rule.waitForIdle()

        assertFalse(
            "After toggle: expected isAppearanceLightStatusBars == false",
            lightStatusBarsFlag(),
        )
    }
}
