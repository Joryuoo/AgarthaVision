package com.agarthavision.ui.verify

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
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
        val speciesConfirmed = mutableListOf<Boolean>()
        val species = mutableListOf<EggSpecies>()
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
        val beganDraw = mutableListOf<Int>()
        val drawnBoxes = mutableListOf<ImageBox>()
        var cancelledDraws = 0
    }

    private fun actionsFor(r: Recorder) = VerificationSheetActions(
        onQ1Selected = { r.q1 += it },
        onQ2Selected = { r.q2 += it },
        onSpeciesConfirmed = { r.speciesConfirmed += it },
        onSpeciesSelected = { r.species += it },
        onOtherSpeciesChanged = {},
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
        onBeginDraw = { r.beganDraw += it },
        onBoxDrawn = { r.drawnBoxes += it },
        onCancelDraw = { r.cancelledDraws++ },
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
        speciesConfirmed: Boolean? = null,
        species: EggSpecies? = null,
        otherSpeciesText: String = "",
    ) = VerificationAnswers(
        isEgg = isEgg,
        isBoxCorrect = isBoxCorrect,
        speciesConfirmed = speciesConfirmed,
        species = species,
        otherSpeciesText = otherSpeciesText,
    )

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
    fun `a misplaced box still reaches the species question`() {
        // The chain used to stop here, on both branches, and it must not. A misplaced box
        // still holds a countable egg: skipping the species question drops that egg from the
        // per-species count, and - since `Finding.isComplete` asks for a species on a
        // BOX_INCORRECT row - leaves the frame permanently unsubmittable. The verdict
        // recorded for the box is still BOX_INCORRECT.
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = false))))

        option(VerifyTestTags.QUESTION_Q3, "Yes").assertIsDisplayed()
    }

    /**
     * The species step asks about the model's suggestion first. The common answer is yes,
     * and a yes must not cost the medtech a pick from a list they just agreed with — so the
     * picker only appears once they say the suggestion is wrong.
     */
    @Test
    fun `a correct box asks whether the suggested species is right, not for a pick`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        composeRule.onNodeWithText("Is this egg ${EggSpecies.ASCARIS.displayName}?")
            .performScrollTo()
            .assertIsDisplayed()
        option(VerifyTestTags.QUESTION_Q3, "Yes").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `answering the species question reports the answer`() {
        val r = setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        option(VerifyTestTags.QUESTION_Q3, "No").performClick()

        assertEquals(listOf(false), r.speciesConfirmed)
    }

    @Test
    fun `an unanswered box question stops the chain before the species question`() {
        setContent(state(answers = listOf(answered(isEgg = true))))

        composeRule.onNodeWithTag(
            VerifyTestTags.questionOption(VerifyTestTags.QUESTION_Q3, "Yes"),
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `agreeing with the suggested species keeps the picker hidden`() {
        setContent(
            state(
                answers = listOf(
                    answered(
                        isEgg = true,
                        isBoxCorrect = true,
                        speciesConfirmed = true,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `rejecting the suggested species reveals the picker`() {
        setContent(
            state(answers = listOf(answered(isEgg = true, isBoxCorrect = true, speciesConfirmed = false))),
        )

        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    /** A class the app cannot map to a species has nothing to confirm, so ask for a pick. */
    @Test
    fun `an unrecognised model class skips the question and shows the picker`() {
        val unknownClass = frame().copy(
            predictions = listOf(Prediction("Giardia", 0.9f, 100f, 100f, 50f, 50f)),
        )
        setContent(
            state(
                answers = listOf(answered(isEgg = true, isBoxCorrect = true)),
                frame = unknownClass,
            ),
        )

        composeRule.onNodeWithTag(
            VerifyTestTags.questionOption(VerifyTestTags.QUESTION_Q3, "Yes"),
        ).assertDoesNotExist()
        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
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
                    answered(
                        isEgg = true,
                        isBoxCorrect = true,
                        speciesConfirmed = false,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            ),
        )

        // Scoped to the dropdown. The "will be saved" summary renders the same species
        // name, and the detection card renders the model's class label, so an unscoped text
        // lookup matches more than one node.
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
    fun `rejecting the suggested species without picking another does not complete`() {
        setContent(
            state(answers = listOf(answered(isEgg = true, isBoxCorrect = true, speciesConfirmed = false))),
        )

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
    fun `one unanswered detection among several blocks submit`() {
        setContent(
            state(
                answers = listOf(answered(isEgg = false), answered()),
                frame = frame(predictions = 2),
            ),
        )

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsNotEnabled()
    }

    /**
     * An AI capture the model returned nothing for is a normal negative result and the most
     * common one in surveillance. It has nothing to fill in, so it must be recordable as it
     * stands - first the "answers must be non-empty" gate made it permanently un-submittable,
     * then a missed-egg answer was demanded of it, which was a tap asking the medtech to restate
     * the absence of work.
     */
    @Test
    fun `a clean field submits as it stands`() {
        setContent(state(answers = emptyList(), frame = frame(predictions = 0)))

        sheetNode(VerifyTestTags.SHEET_PRIMARY_ACTION).assertIsEnabled()
    }

    @Test
    fun `a frame with no model output submits as it stands too`() {
        setContent(noModelOutputState())

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
        // Q1 must not be reported for a Q2 tap. Both questions render the same Yes/No labels.
        assertEquals(emptyList<Boolean>(), r.q1)
    }

    /**
     * "Did the model miss any eggs in this frame?" is derived from the findings, not asked.
     *
     * Recording an egg the model never boxed already says the model missed one, so a second
     * control asking the same thing is a place for the two to disagree. There is no Q4 tag left
     * to look up, so this asserts on the question's text instead.
     */
    @Test
    fun `there is no missed-egg question to answer`() {
        setContent(state())

        composeRule.onNodeWithText("Did the model miss any eggs in this frame?")
            .assertDoesNotExist()
    }

    // Detection navigation

    /**
     * The egg buttons sit under the species card and step between the boxes on one frame.
     * Each side is dead at its own end of the range, so on the first egg only "next" can
     * fire, and on the last only "previous" — the pair can never look like it leaves the
     * frame.
     */
    @Test
    fun `on the first egg only next fires`() {
        val r = setContent(state(answers = listOf(answered(), answered()), frame = frame(predictions = 2)))

        sheetNode(VerifyTestTags.DETECTION_PREV).assertIsNotEnabled()
        sheetNode(VerifyTestTags.DETECTION_NEXT).assertIsEnabled().performClick()

        assertEquals(0, r.detectionPrev)
        assertEquals(1, r.detectionNext)
    }

    @Test
    fun `on the last egg only previous fires`() {
        val r = setContent(
            state(answers = listOf(answered(), answered()), frame = frame(predictions = 2))
                .copy(currentDetectionIndex = 1),
        )

        sheetNode(VerifyTestTags.DETECTION_NEXT).assertIsNotEnabled()
        sheetNode(VerifyTestTags.DETECTION_PREV).assertIsEnabled().performClick()

        assertEquals(1, r.detectionPrev)
        assertEquals(0, r.detectionNext)
    }

    @Test
    fun `the detection counter reflects how many boxes there are`() {
        setContent(state(answers = listOf(answered(), answered()), frame = frame(predictions = 2)))

        composeRule.onNodeWithText("Detection 1 of 2").performScrollTo().assertIsDisplayed()
    }

    /**
     * A single-box frame keeps the cycle controls and dims both ends.
     *
     * They used to be hidden below two boxes. Present-and-dead is the same rule the sample
     * cycle above already follows, and one rule for both is what keeps the row from moving
     * under the medtech's thumb as they page between frames with different box counts.
     */
    @Test
    fun `a single-box frame says which detection it is and dims both cycle ends`() {
        setContent(state())

        composeRule.onNodeWithText("Detection 1 of 1").performScrollTo().assertIsDisplayed()
        sheetNode(VerifyTestTags.DETECTION_PREV).assertIsNotEnabled()
        sheetNode(VerifyTestTags.DETECTION_NEXT).assertIsNotEnabled()
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

    // Model output - the three states, which must never collapse into two

    /**
     * The distinction this suite exists to protect.
     *
     * A clean field and an unreachable container both leave the medtech with no boxes to answer
     * questions about, and they mean opposite things: one is a real negative result, the most
     * common one in surveillance, and the other is a broken container. If these two assertions
     * ever pass against the same string, a negative smear has become indistinguishable from an
     * outage on the screen where the medtech decides what to record.
     */
    @Test
    fun `a clean field reads as a result, not as a missing one`() {
        setContent(state(frame = frame(source = FrameSource.MODEL, predictions = 0), answers = emptyList()))

        sheetNode(VerifyTestTags.MODEL_OUTPUT_PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("No eggs detected.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an unreachable container says so, and says the field was still recorded`() {
        setContent(state(frame = frame(source = FrameSource.MANUAL, predictions = 0), answers = emptyList()))

        sheetNode(VerifyTestTags.MODEL_OUTPUT_PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("No model output.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("No eggs detected.").assertDoesNotExist()
    }

    @Test
    fun `model output summarises the classes it named and totals them`() {
        setContent(state(answers = listOf(answered(), answered()), frame = frame(predictions = 2)))

        sheetNode(VerifyTestTags.MODEL_OUTPUT_PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("Ascaris").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TOTAL").performScrollTo().assertIsDisplayed()
    }

    /**
     * The model names a species before the medtech has answered anything, so a frame with model
     * output must carry the caution that it is a suggestion, not a finding (C7). A frame with no
     * model output has nothing to caution about.
     */
    @Test
    fun `a frame with model output carries the AI-suggestion caution`() {
        setContent(state(frame = frame(source = FrameSource.MODEL)))

        sheetNode(VerifyTestTags.AI_SUGGESTION_NOTE).assertIsDisplayed()
    }

    @Test
    fun `an unreachable container renders no AI-suggestion caution`() {
        setContent(state(frame = frame(source = FrameSource.MANUAL), answers = emptyList()))

        composeRule.onNodeWithTag(VerifyTestTags.AI_SUGGESTION_NOTE).assertDoesNotExist()
    }

    @Test
    fun `a clean field renders no AI-suggestion caution either`() {
        setContent(state(frame = frame(source = FrameSource.MODEL, predictions = 0), answers = emptyList()))

        composeRule.onNodeWithTag(VerifyTestTags.AI_SUGGESTION_NOTE).assertDoesNotExist()
    }

    @Test
    fun `a frame out of the queue shows that instead of a position`() {
        // frameIndexInQueue 0 means the frame is no longer in the queue - deleted from the
        // queue screen, or tombstoned. Showing "Sample 0 of 4" was the bug.
        setContent(state(frameIndexInQueue = 0, queueSize = 4))

        composeRule.onNodeWithText("Not in queue").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sample 0 of 4").assertDoesNotExist()
    }

    @Test
    fun `the sample indicator says where in the queue this frame is`() {
        setContent(state(frameIndexInQueue = 2, queueSize = 3))

        composeRule.onNodeWithText("Sample 2 of 3").performScrollTo().assertIsDisplayed()
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

    // No model output — verified through the same Add Egg section as every other frame

    /** A frame captured while the inference container was unreachable. */
    private fun noModelOutputState(findings: List<Finding> = emptyList()) = VerificationUiState(
        frame = frame(source = FrameSource.MANUAL, predictions = 0),
        frameIndexInQueue = 1,
        queueSize = 1,
        findings = findings,
    )

    /**
     * Add Egg is present on a frame with no model output, and it is the only way to verify one.
     *
     * The species checklist this replaced was a second, parallel set of controls reached only on
     * this kind of frame. Losing this path would silently strand every field a medtech captured
     * during an outage.
     */
    @Test
    fun `a frame with no model output still offers Add Egg`() {
        setContent(noModelOutputState())

        sheetNode(VerifyTestTags.ADD_EGG).assertIsDisplayed()
    }

    @Test
    fun `a frame with model output offers Add Egg too`() {
        setContent(state())

        sheetNode(VerifyTestTags.ADD_EGG).assertIsDisplayed()
    }

    @Test
    fun `tapping Add Egg reports it`() {
        val r = setContent(noModelOutputState())

        sheetNode(VerifyTestTags.ADD_EGG).performClick()

        assertEquals(1, r.addFindings)
    }

    /**
     * A frame with no model output has no boxes, so none of the box questions are asked - they
     * are questions about a box. What it has is the model-output section saying the container
     * never answered, and Add Egg.
     */
    @Test
    fun `a frame with no model output asks no box questions`() {
        setContent(noModelOutputState())

        composeRule.onNodeWithTag(
            VerifyTestTags.questionOption(VerifyTestTags.QUESTION_Q1, "Yes"),
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.BOXES_TOGGLE).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.DETECTION_PREV).assertDoesNotExist()
    }

    @Test
    fun `an added egg shows its species picker and its count`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            eggCount = 1,
                            speciesTouched = true,
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.addedSpeciesDropdown(0)).assertIsDisplayed()
        sheetNode(VerifyTestTags.countField(0)).assertIsDisplayed()
    }

    @Test
    fun `an added egg can be removed`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(Finding(answers = VerificationAnswers(eggCount = 1))),
            ),
        )

        sheetNode(VerifyTestTags.removeFinding(0)).performClick()

        assertEquals(listOf(0), r.removedFindings)
    }

    /**
     * The top bar carries the sample's label, which is the time it was captured — the same label
     * the queue row leads with, so the row the medtech tapped names the screen they land on. It
     * used to read "Verify detection" or "Label sample" depending on the source, which told the
     * medtech what kind of screen they were on rather than which sample they were on.
     */
    @Test
    fun `the top bar is the sample's captured-at label, whatever the source`() {
        setContent(noModelOutputState())

        // Instant.EPOCH formatted in the default zone — asserting the literal string would
        // pass or fail by machine, so this asserts the labels it replaced are gone instead.
        composeRule.onNodeWithText("Label sample").assertDoesNotExist()
        composeRule.onNodeWithText("Verify detection").assertDoesNotExist()
    }
}
