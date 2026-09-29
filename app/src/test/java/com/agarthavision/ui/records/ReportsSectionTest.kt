package com.agarthavision.ui.records

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the Reports card's generate button (86d4bzm9k).
 *
 * A report covers verified samples only, so a session with none cannot generate one: the button
 * is disabled and the subtitle says why. [GenerateSessionReportUseCaseTest] pins the same rule
 * one layer down, for any caller that is not this button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReportsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a session with no verified samples cannot generate a report and says why`() {
        render(verifiedSamples = emptyList())

        composeRule.onNodeWithText("Verify at least one sample to generate a report.").assertExists()
        composeRule.onNodeWithText("No reports yet.").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Generate PDF report").assertIsNotEnabled()
    }

    @Test
    fun `disabled tap does not call onGenerate`() {
        var generateCalls = 0
        render(verifiedSamples = emptyList(), onGenerate = { generateCalls++ })

        composeRule.onNodeWithContentDescription("Generate PDF report").performClick()

        assertEquals(0, generateCalls)
    }

    @Test
    fun `a session with a verified sample can generate`() {
        render(verifiedSamples = listOf(sample()))

        composeRule.onNodeWithText("No reports yet.").assertExists()
        composeRule.onNodeWithContentDescription("Generate PDF report")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun `tap on enabled button calls onGenerate once`() {
        var generateCalls = 0
        render(verifiedSamples = listOf(sample()), onGenerate = { generateCalls++ })

        composeRule.onNodeWithContentDescription("Generate PDF report").performClick()

        assertEquals(1, generateCalls)
    }

    @Test
    fun `the button stays disabled while a report is generating`() {
        render(verifiedSamples = listOf(sample()), isGenerating = true)

        composeRule.onNodeWithContentDescription("Generating…").assertIsNotEnabled()
    }

    private fun render(
        verifiedSamples: List<SampleUi>,
        isGenerating: Boolean = false,
        onGenerate: () -> Unit = {},
    ) {
        val state = SessionDetailContentState(
            session = SessionDetailUi(
                id = "abcd",
                label = null,
                dateLabel = "2026-09-25",
                timeLabel = "09:41",
                confirmedEggs = 0,
                speciesCount = 0,
                samplesTotal = verifiedSamples.size,
                lpfPerSpecies = emptyMap(),
                detectedSpecies = emptyList(),
                verifiedSamples = verifiedSamples,
            ),
            reports = emptyList(),
            totalReports = 0,
            currentPage = 0,
            isGenerating = isGenerating,
            onGenerate = onGenerate,
            onOpenReport = {},
            onPrevPage = {},
            onNextPage = {},
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                ReportsSection(state = state)
            }
        }
    }

    private fun sample(): SampleUi = SampleUi(
        id = "s-1",
        source = SampleSource.Ai,
        species = "Ascaris lumbricoides",
        confidence = 87,
        filePath = null,
        storagePath = null,
        timeLabel = "09:41",
    )
}
