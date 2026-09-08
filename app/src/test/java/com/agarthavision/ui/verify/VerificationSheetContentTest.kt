package com.agarthavision.ui.verify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Compose UI tests for [VerificationSheetContent], driven through its state/action
 * seam. Phase 1 smoke coverage - the full question-gating suite lands in Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VerificationSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val frame = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    private fun actions(
        onToggleRepeat: () -> Unit = {},
    ) = VerificationSheetActions(
        onQ1Selected = {},
        onQ2Selected = {},
        onSpeciesSelected = {},
        onOtherSpeciesChanged = {},
        onQ4Selected = {},
        onDetectionPrev = {},
        onDetectionNext = {},
        onFramePrev = {},
        onFrameNext = {},
        onDeleteFrame = {},
        onToggleBoundingBoxes = {},
        onSubmit = {},
        onCancel = {},
        onToggleRepeat = onToggleRepeat,
        onUserNoteChanged = {},
    )

    private fun setContent(state: VerificationUiState, actions: VerificationSheetActions = actions()) {
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(state = state, actions = actions)
            }
        }
    }

    @Test
    fun `submit is disabled while any detection is unanswered`() {
        setContent(
            VerificationUiState(
                frame = frame,
                frameIndexInQueue = 1,
                queueSize = 1,
                answers = listOf(VerificationAnswers()),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `submit is enabled once every detection is answered`() {
        setContent(
            VerificationUiState(
                frame = frame,
                frameIndexInQueue = 1,
                queueSize = 1,
                answers = listOf(
                    VerificationAnswers(
                        isEgg = true,
                        isBoxCorrect = true,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `frame navigation is disabled at both ends of a single-frame queue`() {
        setContent(
            VerificationUiState(
                frame = frame,
                frameIndexInQueue = 1,
                queueSize = 1,
                answers = listOf(VerificationAnswers()),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREV).assertIsNotEnabled()
        composeRule.onNodeWithTag(VerifyTestTags.FRAME_NEXT).assertIsNotEnabled()
    }

    @Test
    fun `tapping the repeat flag reports the toggle`() {
        var toggles = 0
        setContent(
            state = VerificationUiState(
                frame = frame,
                frameIndexInQueue = 1,
                queueSize = 1,
                answers = listOf(VerificationAnswers()),
            ),
            actions = actions(onToggleRepeat = { toggles++ }),
        )

        composeRule.onNodeWithTag(VerifyTestTags.REPEAT_TOGGLE).assertIsDisplayed().performClick()

        assertEquals(1, toggles)
    }
}
