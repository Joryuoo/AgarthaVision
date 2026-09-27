package com.agarthavision.ui.capture

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the [Shutter] composable introduced in ticket 86d4auafg.
 *
 * [Shutter] is `internal` so we can reach it from test scope without crossing a module boundary.
 * The production [CaptureScreen] is not instantiated here — no camera, no Hilt graph.
 *
 * The shutter has no busy state any more (14zcqntj6nz): a tap saves the frame and returns, and
 * the model output arrives later from the background queue. What is left to verify:
 * 1. It carries its accessibility label, and a click fires `onClick` exactly once.
 * 2. Taps in quick succession each fire, because nothing locks it between them.
 * 3. With no active session it swallows taps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ShutterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- helpers ----------------------------------------------------------

    private fun setShutter(
        enabled: Boolean,
        onClickCapture: () -> Unit,
    ) {
        composeRule.setContent {
            AgarthaVisionTheme {
                Shutter(
                    enabled = enabled,
                    onClick = onClickCapture,
                )
            }
        }
    }

    // --- tests -----------------------------------------------------------

    @Test
    fun `shutter node exists with its content description`() {
        setShutter(enabled = true, onClickCapture = {})

        val description = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    @Test
    fun `clicking an enabled shutter invokes onClick once`() {
        var clicks = 0
        setShutter(enabled = true, onClickCapture = { clicks++ })

        val description = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(description).performClick()

        assertEquals("expected one click, got $clicks", 1, clicks)
    }

    /**
     * Several fields in a row is the core loop of the screen. Nothing may hold the shutter
     * between taps waiting on a model.
     */
    @Test
    fun `taps in quick succession each fire`() {
        var clicks = 0
        setShutter(enabled = true, onClickCapture = { clicks++ })

        val description = context.getString(R.string.capture_shutter_desc)
        repeat(3) { composeRule.onNodeWithContentDescription(description).performClick() }

        assertEquals("expected three clicks, got $clicks", 3, clicks)
    }

    /**
     * When there is no active session the shutter must carry its label but swallow taps without
     * firing `onClick`.
     */
    @Test
    fun `no-session shutter does not fire onClick`() {
        var clicks = 0
        setShutter(enabled = false, onClickCapture = { clicks++ })

        val description = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(description).performClick()

        assertEquals("disabled shutter must not fire onClick, got $clicks click(s)", 0, clicks)
    }
}
