package com.agarthavision.ui.verify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Verification Queue's inference-state badge (14zcqntj6p0): every state has exactly one badge,
 * each with its own label, and the label is always drawn, so the state never rests on colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InferenceStateBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `every state maps to its own badge with its own label`() {
        val badges = InferenceState.entries.map { InferenceBadge.of(it) }

        assertEquals(InferenceState.entries.size, badges.toSet().size)
        assertEquals(badges.size, badges.map { it.label }.toSet().size)
    }

    @Test
    fun `the badge shows its label for each state`() {
        var state by mutableStateOf(InferenceState.QUEUED)
        composeRule.setContent {
            AgarthaVisionTheme { InferenceStateBadge(state) }
        }

        InferenceState.entries.forEach { current ->
            state = current
            composeRule.waitForIdle()

            val badge = InferenceBadge.of(current)
            composeRule.onNodeWithTag(VerifyTestTags.inferenceBadge(badge), useUnmergedTree = true)
                .assertExists()
            composeRule.onNodeWithText(context.getString(badge.label)).assertExists()
        }
    }
}
