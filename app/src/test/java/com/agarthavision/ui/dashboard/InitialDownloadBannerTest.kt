package com.agarthavision.ui.dashboard

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InitialDownloadBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: InitialDownload) {
        composeRule.setContent { AgarthaVisionTheme { InitialDownloadBanner(initialDownload = state) } }
    }

    @Test
    fun `in progress shows downloading text as a polite live region`() {
        show(InitialDownload.IN_PROGRESS)

        composeRule.onNodeWithText("Downloading your records…").assertIsDisplayed()
        composeRule.onNodeWithText("Your records haven't finished downloading", substring = true)
            .assertDoesNotExist()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        ).assertIsDisplayed()
    }

    @Test
    fun `incomplete shows the connect to the internet warning`() {
        show(InitialDownload.INCOMPLETE)

        composeRule.onNodeWithText(
            "Your records haven't finished downloading. Connect to the internet before working offline.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Downloading your records…").assertDoesNotExist()
    }

    @Test
    fun `done renders nothing`() {
        show(InitialDownload.DONE)

        composeRule.onNodeWithText("Downloading your records…").assertDoesNotExist()
        composeRule.onNodeWithText("Your records haven't finished downloading", substring = true)
            .assertDoesNotExist()
    }
}
