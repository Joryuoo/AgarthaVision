package com.agarthavision.ui.verify

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.ui.components.BiometricLockHost
import com.agarthavision.ui.components.BiometricLockTestTags
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VerificationSheetLockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleFrame() = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    private fun noOpActions(
        onConfirmLeave: () -> Unit = {},
        onDismissLeave: () -> Unit = {},
        onDeleteFrame: () -> Unit = {},
        onCancelInferenceConfirmed: () -> Unit = {},
        onCancelInferenceDismissed: () -> Unit = {},
    ) = VerificationSheetActions(
        onQ1Selected = {},
        onQ2Selected = {},
        onSpeciesConfirmed = {},
        onSpeciesSelected = {},
        onOtherSpeciesChanged = {},
        onDetectionPrev = {},
        onDetectionNext = {},
        onFramePrev = {},
        onFrameNext = {},
        onDeleteFrame = onDeleteFrame,
        onToggleBoundingBoxes = {},
        onSubmit = {},
        onCancel = {},
        onUserNoteChanged = {},
        onAddSpecies = {},
        onRemoveFinding = {},
        onExpandFinding = {},
        onCollapseFinding = {},
        onFieldTotalChanged = { _, _ -> },
        onAddedSpeciesSelected = { _, _ -> },
        onAddedOtherSpeciesChanged = { _, _ -> },
        onBeginDraw = { _, _ -> },
        onBoxDrawn = {},
        onCancelDraw = {},
        onRemoveDrawnBox = { _, _ -> },
        onRemoveReplacementBox = {},
        onConfirmLeave = onConfirmLeave,
        onDismissLeave = onDismissLeave,
        onCancelInferenceConfirmed = onCancelInferenceConfirmed,
        onCancelInferenceDismissed = onCancelInferenceDismissed,
    )

    @Test
    fun `discard edits confirmation dialog is hidden while locked and restored intact after unlock`() {
        var isLocked by mutableStateOf(false)
        var confirmedLeaves = 0
        var dismissedLeaves = 0
        val frame = sampleFrame()
        val uiState = VerificationUiState(
            frame = frame,
            frameIndexInQueue = 1,
            queueSize = 1,
            findings = listOf(
                Finding(
                    prediction = frame.predictions.first(),
                    answers = VerificationAnswers(isEgg = true),
                ),
            ),
            userNote = "In-flight clinical note",
            pendingLeave = LeaveIntent.EXIT,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    VerificationSheetContent(
                        state = uiState,
                        actions = noOpActions(
                            onConfirmLeave = { confirmedLeaves++ },
                            onDismissLeave = { dismissedLeaves++ },
                        ),
                        isEditing = true,
                    )
                }
            }
        }

        // Before locking: "Discard edits?" dialog is open
        composeRule.onNodeWithText("Discard edits?").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_DISMISS).assertIsDisplayed()

        // Background & resume with Biometric Lock active
        isLocked = true
        composeRule.waitForIdle()

        // While locked: lock overlay is displayed and "Discard edits?" dialog does not exist over it
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Discard edits?").assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_DISMISS).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.NOTE_FIELD).assertIsNotDisplayed()

        // System Back while locked does not dismiss or confirm the dialog
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals(0, confirmedLeaves)
        assertEquals(0, dismissedLeaves)

        // Unlock: dialog and unsaved note are restored in a coherent state
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertDoesNotExist()
        composeRule.onNodeWithText("Discard edits?").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_DISMISS).assertIsDisplayed().performClick()
        assertEquals(1, dismissedLeaves)
        assertEquals(0, confirmedLeaves)
    }

    @Test
    fun `discard frame dialog remembers open state across lock and unlock without auto dismissing`() {
        var isLocked by mutableStateOf(false)
        var deletes = 0
        val frame = sampleFrame()
        val uiState = VerificationUiState(
            frame = frame,
            frameIndexInQueue = 1,
            queueSize = 1,
            findings = listOf(Finding(prediction = frame.predictions.first(), answers = VerificationAnswers())),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    VerificationSheetContent(
                        state = uiState,
                        actions = noOpActions(onDeleteFrame = { deletes++ }),
                        isEditing = false,
                    )
                }
            }
        }

        // Trigger the Discard frame confirmation dialog (stored in local remember inside VerificationSheetContent)
        composeRule.onNodeWithTag(VerifyTestTags.SHEET_SECONDARY_ACTION)
            .performClick()
        composeRule.onNodeWithTag(VerifyTestTags.DISCARD_DIALOG_CONFIRM).assertIsDisplayed()

        // Lock app
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.DISCARD_DIALOG_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.DISCARD_DIALOG_DISMISS).assertDoesNotExist()
        assertEquals(0, deletes)

        // Unlock app: local remember state preserved so dialog re-opens
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VerifyTestTags.DISCARD_DIALOG_CONFIRM).assertIsDisplayed().performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun `cancel inference dialog and delete samples confirm dialog are hidden while locked and restored on unlock`() {
        var isLocked by mutableStateOf(true)
        var deleteConfirmed = 0
        val frame = sampleFrame()
        val uiState = VerificationUiState(
            frame = frame,
            frameIndexInQueue = 1,
            queueSize = 1,
            findings = listOf(Finding(prediction = frame.predictions.first(), answers = VerificationAnswers())),
            showCancelInferenceConfirm = true,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    VerificationSheetContent(
                        state = uiState,
                        actions = noOpActions(),
                        isEditing = false,
                    )
                    DeleteSamplesConfirmDialog(
                        verifiedCount = 1,
                        unverifiedCount = 2,
                        onConfirm = { deleteConfirmed++ },
                        onDismiss = {},
                    )
                }
            }
        }

        // Hidden while locked
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.CANCEL_INFERENCE_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithText("Delete 3 items?").assertDoesNotExist()

        // Restored after unlock
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VerifyTestTags.CANCEL_INFERENCE_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithText("Delete 3 items?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(1, deleteConfirmed)
    }
}

