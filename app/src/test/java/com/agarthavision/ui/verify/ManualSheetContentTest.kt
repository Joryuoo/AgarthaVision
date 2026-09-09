package com.agarthavision.ui.verify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTextInput
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Compose UI tests for [ManualSheetContent], driven through its state/action seam -
 * no Hilt graph and no ViewModel. Runs on the JVM under Robolectric, inside the
 * `:app:testDebugUnitTest` task Husky already enforces.
 *
 * These cover what the sheet *does* with a given [ManualCaptureUiState]. Two gaps are
 * deliberate:
 * - **How it looks.** The `ContentScale.Fit` preview fix this suite was written after
 *   produces no semantics difference; guarding it needs a screenshot test.
 * - **Inside the custom-species dialog.** An `AlertDialog` holding an `OutlinedTextField`
 *   never reaches idle under Robolectric - any node lookup after that dialog opens spins
 *   until Espresso's 60s timeout, and one variant exhausted the heap inside
 *   `ShadowLineBreaker`. The discard dialog, whose content is plain text, is fine. That
 *   path needs an instrumented test on a device; only the chip that opens it is covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ManualSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val frame = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
    )

    /** Collects what the sheet reports, so assertions read as "the UI asked for X". */
    private class Recorder {
        val species = mutableListOf<EggSpecies>()
        val otherSpecies = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var prev = 0
        var next = 0
        var deletes = 0
        var submits = 0
        var cancels = 0
    }

    private fun actionsFor(r: Recorder) = ManualSheetActions(
        onSpeciesSelected = { r.species += it },
        onOtherSpeciesChanged = { r.otherSpecies += it },
        onUserNoteChanged = { r.notes += it },
        onFramePrev = { r.prev++ },
        onFrameNext = { r.next++ },
        onDeleteFrame = { r.deletes++ },
        onSubmit = { r.submits++ },
        onCancel = { r.cancels++ },
    )

    private fun state(
        frameIndexInQueue: Int = 1,
        queueSize: Int = 1,
        selectedSpecies: EggSpecies? = null,
        otherSpeciesText: String = "",
        isSubmitting: Boolean = false,
    ) = ManualCaptureUiState(
        frame = frame,
        frameIndexInQueue = frameIndexInQueue,
        queueSize = queueSize,
        selectedSpecies = selectedSpecies,
        otherSpeciesText = otherSpeciesText,
        isSubmitting = isSubmitting,
    )

    /**
     * A node inside the sheet's own `verticalScroll` column.
     *
     * The scroll is not optional. Robolectric lays the sheet out in a fixed viewport, so
     * anything below the fold - the action row especially - has no bounds until it is
     * scrolled in, and `performClick` on an unbounded node is silently a no-op rather
     * than a failure. Reaching for a sheet node without this helper produces a test that
     * passes while asserting nothing.
     */
    private fun sheetNode(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo()

    /** A node in a dialog window, which is not scrollable and must not be scrolled to. */
    private fun dialogNode(tag: String) = composeRule.onNodeWithTag(tag)

    /**
     * Types into a text field and then stops the test clock.
     *
     * A focused Compose text field blinks its caret forever, and Robolectric drives that
     * animation off the same clock `waitForIdle` waits on - so the next node lookup after
     * typing spins until Espresso's 60s timeout and the test fails with AppNotIdleException
     * rather than an assertion. Freezing the clock ends the blink and lets the rest of the
     * interaction run. Typing as the last step of a test hides this, which is why every
     * field goes through here.
     */
    private fun typeInto(node: SemanticsNodeInteraction, text: String) {
        composeRule.mainClock.autoAdvance = false
        node.performTextInput(text)
    }

    private fun setContent(state: ManualCaptureUiState): Recorder {
        val recorder = Recorder()
        composeRule.setContent {
            AgarthaVisionTheme {
                ManualSheetContent(state = state, actions = actionsFor(recorder))
            }
        }
        return recorder
    }

    // Submit gating

    @Test
    fun `submit is disabled until a species is selected`() {
        setContent(state())

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `submit is enabled once a species is present in state`() {
        setContent(state(selectedSpecies = EggSpecies.ASCARIS))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `species OTHER alone does not enable submit without free text`() {
        setContent(state(selectedSpecies = EggSpecies.OTHER, otherSpeciesText = "  "))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `species OTHER with free text enables submit`() {
        setContent(state(selectedSpecies = EggSpecies.OTHER, otherSpeciesText = "Taenia"))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `submit is disabled while a submission is in flight`() {
        setContent(state(selectedSpecies = EggSpecies.ASCARIS, isSubmitting = true))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `tapping submit reports exactly one submission`() {
        val r = setContent(state(selectedSpecies = EggSpecies.ASCARIS))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).performClick()

        assertEquals(1, r.submits)
    }

    @Test
    fun `a disabled submit button does not report a submission`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).performClick()

        assertEquals(0, r.submits)
    }

    // Species selection

    @Test
    fun `tapping a quick species chip reports the selection`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.speciesChip(EggSpecies.TRICHURIS.name)).performClick()

        assertEquals(listOf(EggSpecies.TRICHURIS), r.species)
    }

    @Test
    fun `the Other chip opens the dialog without selecting a species itself`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.SPECIES_CHIP_OTHER).performClick()

        // Opening the dialog must not itself pick a species - only its Save button does.
        // What happens *inside* that dialog is not asserted here: see the class comment.
        assertEquals(emptyList<EggSpecies>(), r.species)
        assertEquals(emptyList<String>(), r.otherSpecies)
    }

    // Discard flow

    @Test
    fun `discard does not delete until the dialog is confirmed`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.SHEET_SECONDARY_ACTION).performClick()

        dialogNode(VerifyTestTags.DISCARD_DIALOG_CONFIRM).assertIsDisplayed()
        assertEquals(0, r.deletes)
    }

    @Test
    fun `confirming the discard dialog deletes the frame`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.SHEET_SECONDARY_ACTION).performClick()
        dialogNode(VerifyTestTags.DISCARD_DIALOG_CONFIRM).performClick()

        assertEquals(1, r.deletes)
    }

    @Test
    fun `dismissing the discard dialog leaves the frame alone`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.SHEET_SECONDARY_ACTION).performClick()
        dialogNode(VerifyTestTags.DISCARD_DIALOG_DISMISS).performClick()

        assertEquals(0, r.deletes)
    }

    // Queue navigation

    @Test
    fun `both navigation ends are disabled on a single-frame queue`() {
        setContent(state(frameIndexInQueue = 1, queueSize = 1))

        sheetNode(VerifyTestTags.FRAME_PREV).assertIsNotEnabled()
        sheetNode(VerifyTestTags.FRAME_NEXT).assertIsNotEnabled()
    }

    @Test
    fun `the middle of a queue enables both directions`() {
        val r = setContent(state(frameIndexInQueue = 2, queueSize = 3))

        sheetNode(VerifyTestTags.FRAME_PREV).assertIsEnabled().performClick()
        sheetNode(VerifyTestTags.FRAME_NEXT).assertIsEnabled().performClick()

        assertEquals(1, r.prev)
        assertEquals(1, r.next)
    }

    @Test
    fun `the last frame of a queue cannot go forward`() {
        setContent(state(frameIndexInQueue = 3, queueSize = 3))

        sheetNode(VerifyTestTags.FRAME_PREV).assertIsEnabled()
        sheetNode(VerifyTestTags.FRAME_NEXT).assertIsNotEnabled()
    }

    // Note field

    @Test
    fun `typing in the note field reports the change`() {
        val r = setContent(state())

        typeInto(sheetNode(VerifyTestTags.NOTE_FIELD), "clumped")

        assertEquals(listOf("clumped"), r.notes)
    }

    @Test
    fun `the note field is disabled while a submission is in flight`() {
        setContent(state(selectedSpecies = EggSpecies.ASCARIS, isSubmitting = true))

        sheetNode(VerifyTestTags.NOTE_FIELD).assertIsNotEnabled()
    }

    // Empty state

    @Test
    fun `a null frame renders no sheet chrome at all`() {
        setContent(ManualCaptureUiState(frame = null))

        composeRule.onNodeWithTag(VerifyTestTags.SHEET_PRIMARY_ACTION).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).assertDoesNotExist()
    }

    @Test
    fun `the frame preview is present when a frame is set`() {
        setContent(state())

        sheetNode(VerifyTestTags.FRAME_PREVIEW).assertIsDisplayed()
    }

    // Back navigation

    @Test
    fun `the top bar back control cancels the sheet`() {
        val r = setContent(state())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, r.cancels)
    }
}
