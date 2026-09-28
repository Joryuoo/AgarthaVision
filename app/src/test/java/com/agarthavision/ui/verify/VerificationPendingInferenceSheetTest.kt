package com.agarthavision.ui.verify

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The Verification Screen for a sample still waiting on inference (14zcqntj6p1): the indicator
 * and its text, the way out of waiting, and annotation controls that stay disabled until the
 * model output lands or the medtech cancels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VerificationPendingInferenceSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class Recorder {
        var cancelRequests = 0
        var cancelConfirms = 0
        var cancelDismissals = 0
    }

    private fun frame(state: InferenceState) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = if (state == InferenceState.MANUAL) FrameSource.MANUAL else FrameSource.MODEL,
        inferenceState = state,
    )

    private fun show(
        state: InferenceState,
        showCancelConfirm: Boolean = false,
    ): Recorder {
        val recorder = Recorder()
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(
                    state = VerificationUiState(
                        frame = frame(state),
                        frameIndexInQueue = 1,
                        queueSize = 1,
                        showCancelInferenceConfirm = showCancelConfirm,
                    ),
                    actions = VerificationSheetActions(
                        onCancelInferenceRequested = { recorder.cancelRequests++ },
                        onCancelInferenceConfirmed = { recorder.cancelConfirms++ },
                        onCancelInferenceDismissed = { recorder.cancelDismissals++ },
                    ),
                )
            }
        }
        return recorder
    }

    private fun sheetNode(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo()

    private fun pendingText() = sheetNode(VerifyTestTags.MODEL_OUTPUT_PENDING)
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString()

    @Test
    fun `a queued sample shows the indicator and says it is queued, not in inference`() {
        show(InferenceState.QUEUED)

        sheetNode(VerifyTestTags.MODEL_OUTPUT_SPINNER).assertExists()
        assertEquals(context.getString(R.string.verify_model_queued), pendingText())
        sheetNode(VerifyTestTags.CANCEL_INFERENCE).assertExists()
    }

    /** The same split the queue's badge makes, so one frame never reads two ways. */
    @Test
    fun `the frame being run says it is in inference`() {
        show(InferenceState.IN_INFERENCE)

        assertEquals(context.getString(R.string.verify_model_in_inference), pendingText())
        sheetNode(VerifyTestTags.CANCEL_INFERENCE).assertExists()
    }

    @Test
    fun `annotation controls are disabled while pending`() {
        show(InferenceState.IN_INFERENCE)

        sheetNode(VerifyTestTags.ADD_SPECIES).assertIsNotEnabled()
        sheetNode(VerifyTestTags.NOTE_FIELD).assertIsNotEnabled()
        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `a manual sample is open for annotation and offers no cancel`() {
        show(InferenceState.MANUAL)

        sheetNode(VerifyTestTags.ADD_SPECIES).assertIsEnabled()
        sheetNode(VerifyTestTags.NOTE_FIELD).assertIsEnabled()
        composeRule.onNodeWithTag(VerifyTestTags.CANCEL_INFERENCE).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.MODEL_OUTPUT_PENDING).assertDoesNotExist()
    }

    @Test
    fun `cancel asks first, and does not cancel on its own`() {
        val recorder = show(InferenceState.QUEUED)

        sheetNode(VerifyTestTags.CANCEL_INFERENCE).performClick()

        assertEquals(1, recorder.cancelRequests)
        assertEquals(0, recorder.cancelConfirms)
    }

    @Test
    fun `the confirmation reports confirm and dismiss`() {
        val recorder = show(InferenceState.QUEUED, showCancelConfirm = true)

        composeRule.onNodeWithTag(VerifyTestTags.CANCEL_INFERENCE_DISMISS).performClick()
        composeRule.onNodeWithTag(VerifyTestTags.CANCEL_INFERENCE_CONFIRM).performClick()

        assertEquals(1, recorder.cancelDismissals)
        assertEquals(1, recorder.cancelConfirms)
    }
}
