package com.agarthavision.ui.records

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the grouped detection list on the Sample Data Screen (14zcqnthrx4).
 *
 * [DetectionGroupsTest] pins the split itself; this pins what the screen does with it: which
 * heading a row lands under, that rejected boxes open hidden but stay one tap away, and that a
 * misplaced row with no box offers nothing to toggle. Driven through [SampleDetailContent], so no
 * Hilt and no ViewModel. Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SampleDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a sample with only confirmed eggs shows no misplaced or rejected heading`() {
        render(
            detection("a", DetectionVerdict.CONFIRMED, label = "Ascaris lumbricoides"),
            detection("b", DetectionVerdict.WRONG_CLASS, label = "Hookworm"),
        )

        composeRule.onNodeWithText("DETECTIONS").assertExists()
        composeRule.onNodeWithTag(SampleDetailTestTags.MISPLACED_HEADING).assertDoesNotExist()
        composeRule.onNodeWithTag(SampleDetailTestTags.REJECTED_HEADING).assertDoesNotExist()
        composeRule.onNodeWithText("No eggs confirmed on this sample.").assertDoesNotExist()
    }

    @Test
    fun `confirmed boxes start drawn`() {
        render(detection("a", DetectionVerdict.CONFIRMED, label = "Ascaris lumbricoides"))

        composeRule.onNodeWithContentDescription("Hide the box for Ascaris lumbricoides").assertExists()
    }

    @Test
    fun `a rejected detection sits under its own heading with its box hidden`() {
        render(
            detection("kept", DetectionVerdict.CONFIRMED, label = "Ascaris lumbricoides"),
            detection("gone", DetectionVerdict.FALSE_POSITIVE, label = "Trichuris trichiura"),
        )

        scrollTo(SampleDetailTestTags.REJECTED_HEADING)
        composeRule.onNodeWithText("REJECTED").assertExists()
        composeRule.onNodeWithText("Not an egg, not counted").assertExists()
        composeRule.onNodeWithContentDescription("Show the box for Trichuris trichiura").assertExists()
    }

    @Test
    fun `a rejected box is one tap away`() {
        render(detection("gone", DetectionVerdict.FALSE_POSITIVE, label = "Trichuris trichiura"))

        scrollTo(SampleDetailTestTags.detectionRow(0))
        composeRule.onNodeWithTag(SampleDetailTestTags.detectionRow(0)).performClick()

        composeRule.onNodeWithContentDescription("Hide the box for Trichuris trichiura").assertExists()
    }

    @Test
    fun `a sample whose every detection was rejected says no eggs were confirmed`() {
        render(
            detection("a", DetectionVerdict.FALSE_POSITIVE, label = "Ascaris lumbricoides"),
            detection("b", DetectionVerdict.FALSE_POSITIVE, label = "Hookworm"),
        )

        composeRule.onNodeWithText("No eggs confirmed on this sample.").assertExists()
        scrollTo(SampleDetailTestTags.REJECTED_HEADING)
        composeRule.onNodeWithTag(SampleDetailTestTags.REJECTED_HEADING).assertExists()
    }

    @Test
    fun `a misplaced egg with no box is listed as counted and cannot be toggled`() {
        render(
            detection("kept", DetectionVerdict.CONFIRMED, label = "Ascaris lumbricoides"),
            detection("moved", DetectionVerdict.BOX_INCORRECT, label = "Hookworm", boxed = false),
        )

        scrollTo(SampleDetailTestTags.MISPLACED_HEADING)
        composeRule.onNodeWithText("MISPLACED BOX").assertExists()
        composeRule.onNodeWithText("Counted, location not recorded").assertExists()
        composeRule.onNodeWithTag(SampleDetailTestTags.detectionRow(1)).assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("Show the box for Hookworm").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Hide the box for Hookworm").assertDoesNotExist()
    }

    @Test
    fun `a misplaced box the medtech redrew stays with the confirmed eggs`() {
        render(detection("redrawn", DetectionVerdict.BOX_INCORRECT, label = "Hookworm"))

        composeRule.onNodeWithTag(SampleDetailTestTags.MISPLACED_HEADING).assertDoesNotExist()
        composeRule.onNodeWithTag(SampleDetailTestTags.detectionRow(0)).assertHasClickAction()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun render(vararg detections: Detection) {
        val item = SampleRecordItem(
            sample = Sample(
                id = "sample-1",
                userId = "user-1",
                deviceId = "device-1",
                sessionId = "session-1",
                filePath = "",
            ),
            detections = detections.toList(),
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                SampleDetailContent(
                    item = item,
                    imageSource = SampleImageSource.Unavailable(SampleImageUnavailableReason.NO_STORAGE_PATH),
                    onBack = {},
                    onViewDetection = {},
                )
            }
        }
    }

    private fun detection(
        id: String,
        verdict: DetectionVerdict,
        label: String,
        boxed: Boolean = true,
    ): Detection = Detection(
        id = id,
        sampleId = "sample-1",
        classLabel = label,
        confidence = 0.87f,
        bboxX = if (boxed) 320f else null,
        bboxY = if (boxed) 320f else null,
        bboxW = if (boxed) 40f else null,
        bboxH = if (boxed) 40f else null,
        verdict = verdict,
        expertClass = null,
    )
}
