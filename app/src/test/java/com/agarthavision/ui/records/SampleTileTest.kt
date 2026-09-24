package com.agarthavision.ui.records

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for how a Records sample card names its sample (14zcqnthrx4).
 *
 * A model-captured sample whose every detection the medtech rejected has no species. Its card
 * reads "No eggs" rather than the species the medtech ruled out, and it is not announced as a
 * manual capture just because it has no confidence left to read out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SampleTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a confirmed sample is announced with its species and confidence`() {
        render(sample(species = "Ascaris lumbricoides", confidence = 87))

        composeRule
            .onNodeWithContentDescription("Sample s-1, Ascaris lumbricoides, 87 percent confidence")
            .assertExists()
    }

    @Test
    fun `a sample with every detection rejected reads no eggs and is not called manual`() {
        render(sample(species = null, confidence = null))

        composeRule.onNodeWithContentDescription("Sample s-1, No eggs").assertExists()
    }

    @Test
    fun `a manual capture is still announced as one`() {
        render(sample(species = "Manual", confidence = null, source = SampleSource.Manual))

        composeRule.onNodeWithContentDescription("Sample s-1, Manual, manual capture").assertExists()
    }

    private fun render(sample: SampleUi) {
        composeRule.setContent {
            AgarthaVisionTheme {
                SampleTile(sample = sample, onClick = {})
            }
        }
    }

    private fun sample(
        species: String?,
        confidence: Int?,
        source: SampleSource = SampleSource.Ai,
    ): SampleUi = SampleUi(
        id = "s-1",
        source = source,
        species = species,
        confidence = confidence,
        filePath = null,
        storagePath = null,
        timeLabel = "09:41",
    )
}
