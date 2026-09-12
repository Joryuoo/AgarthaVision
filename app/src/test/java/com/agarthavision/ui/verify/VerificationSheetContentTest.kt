package com.agarthavision.ui.verify

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
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
 * Compose UI tests for [VerificationSheetContent], driven through its state/action seam -
 * no Hilt graph and no ViewModel. Runs on the JVM under Robolectric, inside the
 * `:app:testDebugUnitTest` task Husky already enforces.
 *
 * The bulk of this suite is the question chain: Q2 appears only once Q1 is yes, the species
 * picker only once Q2 is also yes, and submit unlocks only when every detection is complete.
 * That branching is what makes the sheet easy to break from a small edit.
 *
 * Two gaps are deliberate:
 * - **How it looks.** Scale, colour, and placement produce no semantics difference.
 * - **Inside the species dropdown.** Opening [SpeciesDropdown] raises a menu window holding
 *   a text field, the shape that never reaches idle under Robolectric. The tests assert
 *   that the picker appears and disappears with the chain, not what happens inside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VerificationSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun frame(
        source: FrameSource = FrameSource.MODEL,
        predictions: Int = 1,
    ) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = List(predictions) { Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f) },
        source = source,
    )

    /** Collects what the sheet reports, so assertions read as "the UI asked for X". */
    private class Recorder {
        val q1 = mutableListOf<Boolean>()
        val q2 = mutableListOf<Boolean>()
        val q4 = mutableListOf<Boolean>()
        val species = mutableListOf<EggSpecies>()
        val stages = mutableListOf<EggStage>()
        val notes = mutableListOf<String>()
        var detectionPrev = 0
        var detectionNext = 0
        var framePrev = 0
        var frameNext = 0
        var deletes = 0
        var boxToggles = 0
        var submits = 0
        var cancels = 0
        var addFindings = 0
        val removedFindings = mutableListOf<Int>()
        val counts = mutableListOf<Pair<Int, String>>()
        val addedSpecies = mutableListOf<Pair<Int, EggSpecies>>()
        val addedStages = mutableListOf<Pair<Int, EggStage>>()
    }

    private fun actionsFor(r: Recorder) = VerificationSheetActions(
        onQ1Selected = { r.q1 += it },
        onQ2Selected = { r.q2 += it },
        onSpeciesSelected = { r.species += it },
        onOtherSpeciesChanged = {},
        onStageSelected = { r.stages += it },
        onQ4Selected = { r.q4 += it },
        onDetectionPrev = { r.detectionPrev++ },
        onDetectionNext = { r.detectionNext++ },
        onFramePrev = { r.framePrev++ },
        onFrameNext = { r.frameNext++ },
        onDeleteFrame = { r.deletes++ },
        onToggleBoundingBoxes = { r.boxToggles++ },
        onSubmit = { r.submits++ },
        onCancel = { r.cancels++ },
        onUserNoteChanged = { r.notes += it },
        onAddFinding = { r.addFindings++ },
        onRemoveFinding = { r.removedFindings += it },
        onEggCountChanged = { index, text -> r.counts += index to text },
        onAddedSpeciesSelected = { index, species -> r.addedSpecies += index to species },
        onAddedOtherSpeciesChanged = { _, _ -> },
        onAddedStageSelected = { index, stage -> r.addedStages += index to stage },
    )

    /** An unanswered single-detection frame - the state the sheet opens in. */
    private fun state(
        answers: List<VerificationAnswers> = listOf(VerificationAnswers()),
        frame: FlaggedFrame = frame(),
        frameIndexInQueue: Int = 1,
        queueSize: Int = 1,
    ) = VerificationUiState(
        frame = frame,
        frameIndexInQueue = frameIndexInQueue,
        queueSize = queueSize,
        // The sheet renders findings; these tests describe them by their answers, and the
        // prediction each one hangs off comes from the frame in the same position.
        findings = answers.mapIndexed { index, answer ->
            Finding(prediction = frame.predictions.getOrNull(index), answers = answer)
        },
    )

    /**
     * A node inside the sheet's own `verticalScroll` column.
     *
     * The scroll is not optional. Robolectric lays the sheet out in a fixed viewport, so
     * anything below the fold has no bounds until it is scrolled in, and `performClick` on
     * an unbounded node is silently a no-op rather than a failure - a test written without
     * this helper can pass while asserting nothing. This sheet is far taller than the
     * manual one, so most of it starts below the fold.
     */
    private fun sheetNode(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo()

    /** A node in a dialog window, which is not scrollable and must not be scrolled to. */
    private fun dialogNode(tag: String) = composeRule.onNodeWithTag(tag)

    private fun option(question: String, label: String) =
        sheetNode(VerifyTestTags.questionOption(question, label))

    /**
     * Types into a text field and then stops the test clock. A focused Compose text field
     * blinks its caret forever off the same clock `waitForIdle` waits on, so the next node
     * lookup after typing would spin to Espresso's 60s timeout.
     */
    private fun typeInto(node: SemanticsNodeInteraction, text: String) {
        composeRule.mainClock.autoAdvance = false
        node.performTextInput(text)
    }

    private fun setContent(state: VerificationUiState): Recorder {
        val recorder = Recorder()
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(state = state, actions = actionsFor(recorder))
            }
        }
        return recorder
    }

    private fun answered(
        isEgg: Boolean? = null,
        isBoxCorrect: Boolean? = null,
        species: EggSpecies? = null,
        otherSpeciesText: String = "",
        stage: EggStage? = null,
    ) = VerificationAnswers(isEgg, isBoxCorrect, species, otherSpeciesText, stage)

    // The question chain: which sections are visible

    @Test
    fun `an unanswered detection shows only the first question`() {
        setContent(state())

        option(VerifyTestTags.QUESTION_Q1, "Yes").assertIsDisplayed()
        composeRule.onNodeWithTag(
            VerifyTestTags.questionOption(VerifyTestTags.QUESTION_Q2, "Yes"),
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `answering the first question no keeps the rest of the chain hidden`() {
        setContent(state(answers = listOf(answered(isEgg = false))))

        composeRule.onNodeWithTag(
            VerifyTestTags.questionOption(VerifyTestTags.QUESTION_Q2, "Yes"),
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `answering the first question yes reveals the second`() {
        setContent(state(answers = listOf(answered(isEgg = true))))

        option(VerifyTestTags.QUESTION_Q2, "Yes").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `a misplaced box still reaches the species picker`() {
        // The chain used to stop here. It cannot any more: a misplaced box still holds a
        // countable egg, and skipping the species question dropped it from the per-species
        // count. The verdict recorded for the box is still BOX_INCORRECT.
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = false))))

        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `an unanswered box question stops the chain before the species picker`() {
        setContent(state(answers = listOf(answered(isEgg = true))))

        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `a correct box reveals the species picker`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `the stage picker appears once a species with defined stages is selected`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
                ),
            ),
        )

        sheetNode(VerifyTestTags.STAGE_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `the stage picker is hidden for a species with no defined stages`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.OTHER),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.STAGE_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `the stage picker is hidden before a species is selected`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        composeRule.onNodeWithTag(VerifyTestTags.STAGE_DROPDOWN).assertDoesNotExist()
    }

    /**
     * The species field is a filterable combobox: typing a query must never persist unless a
     * result is tapped. Filtering and then moving focus away without picking a result has to
     * revert the field to whatever species is still the committed answer - anything else lets
     * the operator see one species while a different one gets submitted.
     */
    @Test
    fun `filtering species then losing focus without picking a result reverts to the committed species`() {
        val r = setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
                ),
            ),
        )

        // Scoped to the dropdown: the "will be saved" summary now renders the same species
        // name, so an unscoped text lookup matches two nodes.
        val speciesField = composeRule
            .onNode(
                hasAnyAncestor(hasTestTag(VerifyTestTags.SPECIES_DROPDOWN)) and
                    hasText(EggSpecies.ASCARIS.displayName),
            )
        typeInto(speciesField.performScrollTo(), "Tri")
        composeRule.mainClock.autoAdvance = true
        sheetNode(VerifyTestTags.NOTE_FIELD).performClick()

        speciesField.assertIsDisplayed()
        composeRule.onNodeWithText("Ascaris lumbricoidesTri").assertDoesNotExist()
        assertEquals(emptyList<EggSpecies>(), r.species)
    }

    // The question chain: what unlocks submit

    @Test
    fun `submit is disabled while the first question is unanswered`() {
        setContent(state())

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `not an egg completes a detection on its own`() {
        setContent(state(answers = listOf(answered(isEgg = false))))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `an egg with an unanswered box question does not complete`() {
        setContent(state(answers = listOf(answered(isEgg = true))))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `an egg with a misplaced box still needs a species`() {
        // This used to complete on the box answer alone. Under a counting model that silently
        // dropped a real egg from the low-power-field count, so the species question now
        // stands regardless of where the box landed. The verdict still records BOX_INCORRECT.
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = false))))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `an egg with a misplaced box completes once its species is named`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = false, species = EggSpecies.ASCARIS),
                ),
            ),
        )

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `an egg with a correct box needs a species before it completes`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `a fully answered detection unlocks submit`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
                ),
            ),
        )

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `leaving the stage unanswered does not block submit`() {
        // Ascaris defines a stage set, and the picker is shown - but it is not a gate.
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
                ),
            ),
        )

        sheetNode(VerifyTestTags.STAGE_DROPDOWN).assertIsDisplayed()
        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `one unanswered detection among several blocks submit`() {
        setContent(
            state(
                answers = listOf(answered(isEgg = false), answered()),
                frame = frame(predictions = 2),
            ),
        )

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `a clean field blocks submit until the missed-egg question is answered`() {
        // With no findings, that question is the entire content of the review, so it must not
        // default through as null.
        setContent(state(answers = emptyList(), frame = frame(predictions = 0)))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun `a clean field submits on the missed-egg answer alone`() {
        // An AI capture the model returned nothing for is a normal negative result and has to
        // be recordable. The old "answers must be non-empty" gate made it permanently
        // un-submittable, which is the bug this flips.
        setContent(
            state(answers = emptyList(), frame = frame(predictions = 0)).copy(missedEgg = false),
        )

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    // Answer reporting

    @Test
    fun `answering the first question reports the choice`() {
        val r = setContent(state())

        option(VerifyTestTags.QUESTION_Q1, "Yes").performClick()

        assertEquals(listOf(true), r.q1)
    }

    @Test
    fun `answering the second question reports the choice`() {
        val r = setContent(state(answers = listOf(answered(isEgg = true))))

        option(VerifyTestTags.QUESTION_Q2, "No").performClick()

        assertEquals(listOf(false), r.q2)
        // The chain question and the missed-egg question must not be confused for it.
        assertEquals(emptyList<Boolean>(), r.q1)
        assertEquals(emptyList<Boolean>(), r.q4)
    }

    @Test
    fun `the missed-egg question is answerable while the chain is untouched`() {
        val r = setContent(state())

        option(VerifyTestTags.QUESTION_Q4, "Yes").performClick()

        assertEquals(listOf(true), r.q4)
        assertEquals(emptyList<Boolean>(), r.q1)
    }

    // Detection navigation

    @Test
    fun `detection navigation reports both directions`() {
        val r = setContent(state(answers = listOf(answered(), answered()), frame = frame(predictions = 2)))

        sheetNode(VerifyTestTags.DETECTION_PREV).performClick()
        sheetNode(VerifyTestTags.DETECTION_NEXT).performClick()

        assertEquals(1, r.detectionPrev)
        assertEquals(1, r.detectionNext)
    }

    @Test
    fun `the detection counter reflects how many there are`() {
        setContent(state(answers = listOf(answered(), answered()), frame = frame(predictions = 2)))

        composeRule.onNodeWithText("Detection 1 of 2").performScrollTo().assertIsDisplayed()
    }

    // Bounding boxes

    @Test
    fun `the box switch is on when boxes are shown`() {
        setContent(state())

        sheetNode(VerifyTestTags.BOXES_TOGGLE).assertIsOn()
    }

    @Test
    fun `the box switch is off when boxes are hidden`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(
                    state = state().copy(showBoundingBoxes = false),
                    actions = actionsFor(Recorder()),
                )
            }
        }

        sheetNode(VerifyTestTags.BOXES_TOGGLE).assertIsOff()
    }

    @Test
    fun `toggling the box switch reports the change`() {
        val r = setContent(state())

        sheetNode(VerifyTestTags.BOXES_TOGGLE).performClick()

        assertEquals(1, r.boxToggles)
    }

    // Provenance and repeat

    @Test
    fun `a model frame is badged as AI-suggested`() {
        setContent(state(frame = frame(source = FrameSource.MODEL)))

        sheetNode(VerifyTestTags.SOURCE_BADGE).onChild().assertTextEquals("AI-suggested")
    }

    @Test
    fun `a manual frame is badged as manual`() {
        setContent(state(frame = frame(source = FrameSource.MANUAL)))

        sheetNode(VerifyTestTags.SOURCE_BADGE).onChild().assertTextEquals("Manual")
    }

    @Test
    fun `a frame out of the queue shows that instead of a position`() {
        // frameIndexInQueue 0 means the frame is no longer in the queue - deleted from the
        // queue screen, or tombstoned. Showing "Frame 0/4" was the bug.
        setContent(state(frameIndexInQueue = 0, queueSize = 4))

        // Matched on the label alone: the meta also carries a wall-clock time formatted in
        // the default zone, so asserting the whole string would pass or fail by machine.
        composeRule.onNodeWithText("NOT IN QUEUE", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("FRAME 0/4", substring = true).assertDoesNotExist()
    }

    // Frame navigation

    @Test
    fun `both navigation ends are disabled on a single-frame queue`() {
        setContent(state())

        sheetNode(VerifyTestTags.FRAME_PREV).assertIsNotEnabled()
        sheetNode(VerifyTestTags.FRAME_NEXT).assertIsNotEnabled()
    }

    @Test
    fun `the middle of a queue enables both directions`() {
        val r = setContent(state(frameIndexInQueue = 2, queueSize = 3))

        sheetNode(VerifyTestTags.FRAME_PREV).assertIsEnabled().performClick()
        sheetNode(VerifyTestTags.FRAME_NEXT).assertIsEnabled().performClick()

        assertEquals(1, r.framePrev)
        assertEquals(1, r.frameNext)
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

    // Note field, empty state, back

    @Test
    fun `typing in the note field reports the change`() {
        val r = setContent(state())

        typeInto(sheetNode(VerifyTestTags.NOTE_FIELD), "clumped")

        assertEquals(listOf("clumped"), r.notes)
    }

    @Test
    fun `a null frame renders no sheet chrome at all`() {
        setContent(VerificationUiState(frame = null))

        composeRule.onNodeWithTag(VerifyTestTags.SHEET_PRIMARY_ACTION).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).assertDoesNotExist()
    }

    @Test
    fun `the top bar back control cancels the sheet`() {
        val r = setContent(state())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, r.cancels)
    }
}
