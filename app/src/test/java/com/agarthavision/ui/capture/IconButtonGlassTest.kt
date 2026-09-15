package com.agarthavision.ui.capture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
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
 * Compose tests for the [IconButtonGlass] (ImageVector overload) introduced in ticket 86d4ayef8.
 *
 * The overload is `internal` so we can reach it from test scope without crossing a module
 * boundary. The production `CaptureScreen` is not instantiated here — no camera, no Hilt graph.
 *
 * Three properties are verified:
 * 1. The icon is reachable by the accessibility label added by the ticket.
 * 2. Clicking an enabled button fires `onClick`.
 * 3. Clicking a disabled button does NOT fire `onClick`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class IconButtonGlassTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- helper ----------------------------------------------------------

    private fun setButton(
        enabled: Boolean,
        onClickCapture: () -> Unit,
    ) {
        val desc = context.getString(R.string.capture_records_action_desc)
        composeRule.setContent {
            AgarthaVisionTheme {
                IconButtonGlass(
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    contentDescription = desc,
                    enabled = enabled,
                    onClick = onClickCapture,
                )
            }
        }
    }

    // --- tests -----------------------------------------------------------

    /**
     * The accessibility label attached by the ticket must exist so screen readers and
     * automated accessibility scans can find the button.
     */
    @Test
    fun `records button node exists with the expected content description`() {
        setButton(enabled = true, onClickCapture = {})

        val desc = context.getString(R.string.capture_records_action_desc)
        composeRule.onNodeWithContentDescription(desc).assertExists()
    }

    /**
     * When the button is enabled a click must fire `onClick` exactly once.
     */
    @Test
    fun `clicking an enabled button invokes onClick`() {
        var clicks = 0
        setButton(enabled = true, onClickCapture = { clicks++ })

        val desc = context.getString(R.string.capture_records_action_desc)
        composeRule.onNodeWithContentDescription(desc).performClick()

        assertEquals("expected one click, got $clicks", 1, clicks)
    }

    /**
     * When `enabled = false` (no active session) the glass circle must swallow the tap and
     * not fire `onClick` — the contract from [Modifier.glassCircle].
     */
    @Test
    fun `clicking a disabled button does NOT invoke onClick`() {
        var clicks = 0
        setButton(enabled = false, onClickCapture = { clicks++ })

        val desc = context.getString(R.string.capture_records_action_desc)
        composeRule.onNodeWithContentDescription(desc).performClick()

        assertEquals("disabled button should not fire onClick, but got $clicks click(s)", 0, clicks)
    }
}
