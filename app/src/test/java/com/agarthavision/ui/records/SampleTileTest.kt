package com.agarthavision.ui.records

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for how a Records sample tile names its sample.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SampleTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a sample with multiple eggs is announced with its egg count`() {
        render(sample(eggCount = 2, isManual = false))

        composeRule
            .onNodeWithContentDescription("Sample, 2 eggs")
            .assertExists()
    }

    @Test
    fun `a sample with single egg is announced with singular egg count`() {
        render(sample(eggCount = 1, isManual = false))

        composeRule
            .onNodeWithContentDescription("Sample, 1 egg")
            .assertExists()
    }

    @Test
    fun `a sample with zero eggs is announced with zero eggs`() {
        render(sample(eggCount = 0, isManual = false))

        composeRule
            .onNodeWithContentDescription("Sample, 0 eggs")
            .assertExists()
    }

    @Test
    fun `a manual capture is announced with manual capture suffix`() {
        render(sample(eggCount = 3, isManual = true))

        composeRule
            .onNodeWithContentDescription("Sample, 3 eggs, manual capture")
            .assertExists()
    }

    @Test
    fun `tapping a tile calls onClick`() {
        var clicked = false
        composeRule.setContent {
            AgarthaVisionTheme {
                SampleTile(sample = sample(eggCount = 1), onClick = { clicked = true })
            }
        }
        composeRule.onNodeWithContentDescription("Sample, 1 egg").performClick()
        assertTrue(clicked)
    }

    private fun render(sample: SampleUi, onClick: () -> Unit = {}) {
        composeRule.setContent {
            AgarthaVisionTheme {
                SampleTile(sample = sample, onClick = onClick)
            }
        }
    }

    private fun sample(
        eggCount: Int = 0,
        isManual: Boolean = false,
        source: SampleSource = if (isManual) SampleSource.Manual else SampleSource.Ai,
    ): SampleUi = SampleUi(
        id = "s-1",
        source = source,
        species = if (eggCount > 0) "Ascaris" else null,
        confidence = if (source == SampleSource.Ai) 88 else null,
        filePath = null,
        storagePath = null,
        timeLabel = "09:41",
        isManual = isManual,
        eggCount = eggCount,
    )
}
