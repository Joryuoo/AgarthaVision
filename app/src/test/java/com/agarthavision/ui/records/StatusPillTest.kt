package com.agarthavision.ui.records

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import com.agarthavision.domain.model.SessionLinkState
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [StatusPill].
 *
 * Verifies that each [SessionLinkState] value renders the correct localised
 * text string. Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 *
 * Note: [StatusPill] is `internal` (widened from `private`) solely to allow
 * direct invocation from this test module.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class StatusPillTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `SYNCED state renders Synced label`() {
        val expected = context.getString(R.string.report_status_synced)

        composeRule.setContent {
            AgarthaVisionTheme {
                StatusPill(linkState = SessionLinkState.SYNCED)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `PENDING state renders Pending sync label`() {
        val expected = context.getString(R.string.records_status_pending_sync)

        composeRule.setContent {
            AgarthaVisionTheme {
                StatusPill(linkState = SessionLinkState.PENDING)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `UNOWNED state renders Not linked label`() {
        val expected = context.getString(R.string.session_not_linked)

        composeRule.setContent {
            AgarthaVisionTheme {
                StatusPill(linkState = SessionLinkState.UNOWNED)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `NOT_LINKED state renders Not linked label`() {
        val expected = context.getString(R.string.session_not_linked)

        composeRule.setContent {
            AgarthaVisionTheme {
                StatusPill(linkState = SessionLinkState.NOT_LINKED)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

}
