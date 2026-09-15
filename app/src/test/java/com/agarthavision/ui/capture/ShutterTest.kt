package com.agarthavision.ui.capture

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
 * Four properties are verified:
 * 1. Idle state: correct accessibility label; click fires `onClick` exactly once.
 * 2. Busy state: busy label present; idle label absent; click does NOT fire `onClick`.
 * 3. No-session (disabled, not busy): click does not fire `onClick`.
 * 4. Rendering sanity: busy composition survives multiple clock frames without throwing
 *    (guards the infinite arc transition under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ShutterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- helpers ----------------------------------------------------------

    private fun setShutter(
        isBusy: Boolean,
        enabled: Boolean,
        onClickCapture: () -> Unit,
    ) {
        composeRule.setContent {
            AgarthaVisionTheme {
                Shutter(
                    isBusy = isBusy,
                    enabled = enabled,
                    onClick = onClickCapture,
                )
            }
        }
    }

    // --- tests -----------------------------------------------------------

    /**
     * Idle shutter must carry the idle content description and fire `onClick` on tap.
     */
    @Test
    fun `idle shutter node exists with idle content description`() {
        setShutter(isBusy = false, enabled = true, onClickCapture = {})

        val idleDesc = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(idleDesc).assertExists()
    }

    /**
     * When the shutter is idle and enabled a click must fire `onClick` exactly once.
     */
    @Test
    fun `clicking an idle enabled shutter invokes onClick once`() {
        var clicks = 0
        setShutter(isBusy = false, enabled = true, onClickCapture = { clicks++ })

        val idleDesc = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(idleDesc).performClick()

        assertEquals("expected one click, got $clicks", 1, clicks)
    }

    /**
     * While a capture is in flight (`isBusy = true, enabled = false`) the busy description
     * must be present, the idle description must be absent, and tapping must not fire `onClick`.
     */
    @Test
    fun `busy shutter shows busy description and idle description is absent`() {
        // Disable auto-advance so the infinite arc transition does not hang waitForIdle.
        composeRule.mainClock.autoAdvance = false

        setShutter(isBusy = true, enabled = false, onClickCapture = {})
        composeRule.mainClock.advanceTimeBy(16)

        val busyDesc = context.getString(R.string.capture_shutter_busy_desc)
        val idleDesc = context.getString(R.string.capture_shutter_desc)

        composeRule.onNodeWithContentDescription(busyDesc).assertExists()
        assertEquals(
            "idle description must not be present while busy",
            0,
            composeRule.onAllNodesWithContentDescription(idleDesc).fetchSemanticsNodes().size,
        )
    }

    /**
     * While a capture is in flight (`isBusy = true, enabled = false`) tapping the shutter
     * must not fire `onClick`.
     */
    @Test
    fun `busy shutter does not fire onClick on tap`() {
        composeRule.mainClock.autoAdvance = false

        var clicks = 0
        setShutter(isBusy = true, enabled = false, onClickCapture = { clicks++ })
        composeRule.mainClock.advanceTimeBy(16)

        val busyDesc = context.getString(R.string.capture_shutter_busy_desc)
        composeRule.onNodeWithContentDescription(busyDesc).performClick()

        assertEquals("busy shutter must not fire onClick, got $clicks click(s)", 0, clicks)
    }

    /**
     * When there is no active session (`isBusy = false, enabled = false`) the shutter must
     * carry the idle label but swallow taps without firing `onClick`.
     */
    @Test
    fun `no-session shutter does not fire onClick`() {
        var clicks = 0
        setShutter(isBusy = false, enabled = false, onClickCapture = { clicks++ })

        val idleDesc = context.getString(R.string.capture_shutter_desc)
        composeRule.onNodeWithContentDescription(idleDesc).performClick()

        assertEquals("disabled shutter must not fire onClick, got $clicks click(s)", 0, clicks)
    }

    /**
     * Busy composition must survive multiple clock frames without throwing.
     * The infinite arc transition must not crash or hang under Robolectric.
     */
    @Test
    fun `busy shutter renders across multiple clock frames without crashing`() {
        composeRule.mainClock.autoAdvance = false

        setShutter(isBusy = true, enabled = false, onClickCapture = {})

        // Advance across two rotation cycles (1100 ms each) in 600 ms steps.
        repeat(4) { composeRule.mainClock.advanceTimeBy(600) }

        val busyDesc = context.getString(R.string.capture_shutter_busy_desc)
        composeRule.onNodeWithContentDescription(busyDesc).assertExists()
    }
}
