package com.agarthavision.ui.verify

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
// One composable, one fixture. Splitting by concern would duplicate the sheet setup across
// files and make the suggestion cases harder to read against the rest of the sheet.
@Suppress("LargeClass")
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
        val otherSpecies = mutableListOf<String>()
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
        val expandedFindings = mutableListOf<Int>()
        val collapsedFindings = mutableListOf<Int>()
        val counts = mutableListOf<Pair<Int, String>>()
        val addedSpecies = mutableListOf<Pair<Int, EggSpecies>>()
        val beganDraw = mutableListOf<Pair<Int, Int?>>()
        val drawnBoxes = mutableListOf<ImageBox>()
        var cancelledDraws = 0
        val removedBoxes = mutableListOf<Pair<Int, Int>>()
        val removedReplacements = mutableListOf<Int>()
        var confirmedLeaves = 0
        var dismissedLeaves = 0
    }

    private fun actionsFor(r: Recorder) = VerificationSheetActions(
        onQ1Selected = { r.q1 += it },
        onQ2Selected = { r.q2 += it },
        onSpeciesConfirmed = { r.speciesConfirmed += it },
        onSpeciesSelected = { r.species += it },
        onOtherSpeciesChanged = { r.otherSpecies += it },
        onDetectionPrev = { r.detectionPrev++ },
        onDetectionNext = { r.detectionNext++ },
        onFramePrev = { r.framePrev++ },
        onFrameNext = { r.frameNext++ },
        onDeleteFrame = { r.deletes++ },
        onToggleBoundingBoxes = { r.boxToggles++ },
        onSubmit = { r.submits++ },
        onCancel = { r.cancels++ },
        onUserNoteChanged = { r.notes += it },
        onAddSpecies = { r.addFindings++ },
        onRemoveFinding = { r.removedFindings += it },
        onExpandFinding = { r.expandedFindings += it },
        onCollapseFinding = { r.collapsedFindings += it },
        onFieldTotalChanged = { index, text -> r.counts += index to text },
        onAddedSpeciesSelected = { index, species -> r.addedSpecies += index to species },
        onAddedOtherSpeciesChanged = { _, _ -> },
        onBeginDraw = { index, slot -> r.beganDraw += index to slot },
        onBoxDrawn = { r.drawnBoxes += it },
        onCancelDraw = { r.cancelledDraws++ },
        onRemoveDrawnBox = { index, slot -> r.removedBoxes += index to slot },
        onRemoveReplacementBox = { r.removedReplacements += it },
        onConfirmLeave = { r.confirmedLeaves++ },
        onDismissLeave = { r.dismissedLeaves++ },
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

    /**
     * A question's control. One node per question now: the Yes/No pair became a checkbox, so
     * there is nothing left to disambiguate by label and the question's own tag reaches it.
     */
    private fun question(tag: String) = sheetNode(tag)

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

        question(VerifyTestTags.QUESTION_Q1).assertIsDisplayed()
        composeRule.onNodeWithTag(
            VerifyTestTags.QUESTION_Q2,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `answering the first question no keeps the rest of the chain hidden`() {
        setContent(state(answers = listOf(answered(isEgg = false))))

        composeRule.onNodeWithTag(
            VerifyTestTags.QUESTION_Q2,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    /**
     * A ticked Q1 shows both questions under it at once, and an unset answer draws as what it
     * looks like: unticked, with its correction. The chain used to stop at an unset Q2, which
     * hid Q3 behind a box that already read as unticked.
     */
    @Test
    fun `a ticked Q1 shows Q2 and Q3, each unset one carrying its correction`() {
        setContent(state(answers = listOf(answered(isEgg = true))))

        question(VerifyTestTags.QUESTION_Q2).assertIsDisplayed()
        sheetNode(VerifyTestTags.REDRAW_BOX).assertIsDisplayed()
        question(VerifyTestTags.QUESTION_Q3).assertIsDisplayed()
        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `an unticked Q2 always carries the redraw`() {
        setContent(
            state(answers = listOf(answered(isEgg = true, isBoxCorrect = false, speciesConfirmed = true))),
        )

        sheetNode(VerifyTestTags.REDRAW_BOX).assertIsDisplayed()
    }

    @Test
    fun `a ticked Q2 offers no redraw`() {
        setContent(
            state(answers = listOf(answered(isEgg = true, isBoxCorrect = true, speciesConfirmed = true))),
        )

        composeRule.onNodeWithTag(VerifyTestTags.REDRAW_BOX).assertDoesNotExist()
    }

    @Test
    fun `an unticked Q3 always carries the picker, even when never answered`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `a misplaced box still reaches the species question`() {
        // The chain used to stop here, on both branches, and it must not. A misplaced box
        // still holds a countable egg: skipping the species question drops that egg from the
        // per-species count, and - since `Finding.isComplete` asks for a species on a
        // BOX_INCORRECT row - leaves the frame permanently unsubmittable. The verdict
        // recorded for the box is still BOX_INCORRECT.
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = false))))

        question(VerifyTestTags.QUESTION_Q3).assertIsDisplayed()
    }

    /**
     * The species step asks about the model's suggestion first. The common answer is yes,
     * and a yes must not cost the medtech a pick from a list they just agreed with — so the
     * picker only appears once they say the suggestion is wrong.
     */
    @Test
    fun `a correct box asks whether the suggested species is right, not for a pick`() {
        setContent(
            state(answers = listOf(answered(isEgg = true, isBoxCorrect = true, speciesConfirmed = true))),
        )

        composeRule.onNodeWithText("This egg is ${EggSpecies.ASCARIS.displayName}")
            .performScrollTo()
            .assertIsDisplayed()
        question(VerifyTestTags.QUESTION_Q3).assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.SPECIES_DROPDOWN).assertDoesNotExist()
    }

    @Test
    fun `unchecking the species question reports the disagreement`() {
        val r = setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, speciesConfirmed = true),
                ),
            ),
        )

        question(VerifyTestTags.QUESTION_Q3).performClick()

        assertEquals(listOf(false), r.speciesConfirmed)
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
            VerifyTestTags.QUESTION_Q3,
        ).assertDoesNotExist()
        sheetNode(VerifyTestTags.SPECIES_DROPDOWN).assertIsDisplayed()
    }

    @Test
    fun `species dropdown field is not editable`() {
        setContent(
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

        val speciesField = composeRule
            .onNode(
                hasAnyAncestor(hasTestTag(VerifyTestTags.SPECIES_DROPDOWN)) and
                    hasText(EggSpecies.ASCARIS.displayName),
            )
        speciesField.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
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

        question(VerifyTestTags.QUESTION_Q1).performClick()

        assertEquals(listOf(true), r.q1)
    }

    @Test
    fun `unchecking the second question reports the disagreement`() {
        // The state the screen actually opens in: pre-filled from model output. Unchecking is
        // how the medtech says the box is misplaced, and it must report false, not toggle
        // something else - each question owns one control now, but they sit in one column.
        val r = setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = true))))

        question(VerifyTestTags.QUESTION_Q2).performClick()

        assertEquals(listOf(false), r.q2)
        assertEquals("A Q2 tap is not a Q1 answer.", emptyList<Boolean>(), r.q1)
    }

    @Test
    fun `checking an unanswered question reports agreement`() {
        val r = setContent(state(answers = listOf(answered(isEgg = true))))

        question(VerifyTestTags.QUESTION_Q2).performClick()

        assertEquals(listOf(true), r.q2)
    }

    /**
     * A replaced box latches Q2 at "No", and the checkbox has to say so.
     *
     * `onQ2Selected` already refuses the tap, but a Yes/No pair showed that honestly — as a Yes
     * button that would not take. A checkbox that silently ignores a tap reads as a bug, so the
     * latch is rendered as a disabled control rather than left to be discovered.
     */
    @Test
    fun `a replaced box disables the second question instead of ignoring taps`() {
        val r = setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = false).copy(
                        drawnBox = ImageBox(x = 1f, y = 2f, width = 3f, height = 4f),
                        boxReplaced = true,
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.QUESTION_Q2).assertIsNotEnabled()
        sheetNode(VerifyTestTags.BOX_REPLACED_NOTE).assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), r.q2)
    }

    /** A replaced box is not final: the same redraw-or-remove pair an added egg's box has. */
    @Test
    fun `a replaced box offers both redraw and remove`() {
        val r = setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = false, speciesConfirmed = true).copy(
                        drawnBox = ImageBox(x = 1f, y = 2f, width = 3f, height = 4f),
                        boxReplaced = true,
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.REDRAW_BOX).performClick()
        sheetNode(VerifyTestTags.REMOVE_REPLACEMENT_BOX).performClick()

        assertEquals(listOf(0 to null), r.beganDraw)
        assertEquals(listOf(0), r.removedReplacements)
    }

    @Test
    fun `nothing to remove until a box has been replaced`() {
        setContent(state(answers = listOf(answered(isEgg = true, isBoxCorrect = false, speciesConfirmed = true))))

        composeRule.onNodeWithTag(VerifyTestTags.REMOVE_REPLACEMENT_BOX).assertDoesNotExist()
    }

    /** Each box action is an icon with its word beside it, and that word is what TalkBack reads. */
    @Test
    fun `the box actions say what they do beside their icons`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = false, speciesConfirmed = true).copy(
                        drawnBox = ImageBox(x = 1f, y = 2f, width = 3f, height = 4f),
                        boxReplaced = true,
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.REDRAW_BOX).assertTextContains("Redraw")
        sheetNode(VerifyTestTags.REMOVE_REPLACEMENT_BOX).assertTextContains("Remove")
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

        composeRule.onNodeWithText("Verified sample · Editable").performScrollTo().assertIsDisplayed()
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
    // ---- 86d4by5n4 / 86d4by5n5: drawn boxes on the frame, and drawing as one action ----

    /**
     * The toggle used to live inside the Current Detection block, which needs a model box to
     * exist. A manual capture the medtech located eggs on by hand therefore had boxes to hide
     * and no control anywhere that could hide them.
     */
    @Test
    fun `a frame with no model output offers the box toggle once a box is drawn`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 1,
                            drawnBoxes = listOf(ImageBox(x = 10f, y = 10f, width = 4f, height = 4f)),
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.BOXES_TOGGLE).assertExists()
    }

    /**
     * Drawing takes the whole screen rather than happening inside the sheet, which is the whole
     * of 86d4by5n5: the frame is full-width at the capture aspect, so every affordance that
     * starts a drawing sits below it by construction and the medtech had to scroll back up to
     * find the egg they had just decided to aim at.
     */
    @Test
    fun `drawing covers the sheet with a screen holding the frame and nothing else`() {
        setContent(state().copy(drawTarget = DrawTarget(findingIndex = 0)))

        composeRule.onNodeWithTag(VerifyTestTags.DRAW_MODE).assertExists()
        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).assertExists()
        composeRule.onNodeWithTag(VerifyTestTags.DRAW_ACCEPT).assertExists()
        // The sheet is still composed underneath, so its scroll survives, but none of it is
        // reachable while the draw screen covers it.
        composeRule.onNodeWithTag(VerifyTestTags.QUESTION_Q1).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.ADD_SPECIES).assertDoesNotExist()
    }

    /** Leaving the sheet must not cost the medtech the egg they were aiming at. */
    @Test
    fun `the drawing surface names the egg it is for`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 3,
                        ),
                    ),
                ),
            ).copy(drawTarget = DrawTarget(findingIndex = 0, slot = 1)),
        )

        composeRule.onNodeWithTag(VerifyTestTags.DRAW_MODE_TARGET)
            .assertTextContains("Ascaris lumbricoides · egg 2 of 3")
    }

    /** No box drawn yet, so there is nothing to discard and nothing offered. */
    @Test
    fun `an unlocated egg offers no way to remove a box`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.removeDrawnBox(0, 0)).assertDoesNotExist()
    }

    @Test
    fun `a located egg can have its box discarded`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 1,
                            drawnBoxes = listOf(ImageBox(x = 10f, y = 10f, width = 4f, height = 4f)),
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.LOCATE_TOGGLE).performClick()
        sheetNode(VerifyTestTags.removeDrawnBox(0, 0)).performClick()

        assertEquals(listOf(0 to 0), r.removedBoxes)
    }

    /**
     * Save box and Cancel sit under the frame. Laid over the bottom of the image, they covered the
     * pixels being drawn on, so an egg near the lower edge could not be boxed.
     */
    @Test
    fun `the draw buttons sit below the frame, not on it`() {
        setContent(state().copy(drawTarget = DrawTarget(findingIndex = 0)))

        for (tag in listOf(VerifyTestTags.DRAW_ACCEPT, VerifyTestTags.DRAW_CANCEL)) {
            composeRule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(VerifyTestTags.FRAME_PREVIEW)))
                .assertDoesNotExist()
            val button = composeRule.onNodeWithTag(tag).getBoundsInRoot()
            val frame = composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).getBoundsInRoot()
            assertTrue("$tag must be under the frame", button.top >= frame.bottom)
        }
    }

    @Test
    fun `save box waits for a box to be dragged out`() {
        setContent(state().copy(drawTarget = DrawTarget(findingIndex = 0)))

        composeRule.onNodeWithTag(VerifyTestTags.DRAW_ACCEPT).assertIsNotEnabled()
    }

    /**
     * The draw screen is its own screen, not a see-through layer. A tap on its empty space used
     * to land on whatever was underneath it.
     */
    @Test
    fun `a tap on the draw screen never reaches the sheet under it`() {
        val recorder = Recorder()
        val holder = mutableStateOf(state())
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(state = holder.value, actions = actionsFor(recorder))
            }
        }
        val back = composeRule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot

        holder.value = holder.value.copy(drawTarget = DrawTarget(findingIndex = 0))
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(back.center) }

        assertEquals("The sheet's back control took a tap aimed at the draw screen.", 0, recorder.cancels)
    }

    /**
     * A sheet whose draw actions really open and close the draw screen, so a test can follow the
     * medtech out to it and back.
     */
    private fun setDrawableContent(initial: VerificationUiState): Recorder {
        val recorder = Recorder()
        val holder = mutableStateOf(initial)
        val base = actionsFor(recorder)
        composeRule.setContent {
            AgarthaVisionTheme {
                VerificationSheetContent(
                    state = holder.value,
                    actions = base.copy(
                        onBeginDraw = { index, slot ->
                            holder.value = holder.value.copy(drawTarget = DrawTarget(index, slot))
                        },
                        onCancelDraw = { holder.value = holder.value.copy(drawTarget = null) },
                        onBoxDrawn = { box ->
                            recorder.drawnBoxes += box
                            holder.value = holder.value.copy(drawTarget = null)
                        },
                    ),
                )
            }
        }
        return recorder
    }

    private val misplacedBox = listOf(answered(isEgg = true, isBoxCorrect = false, speciesConfirmed = true))

    @Test
    fun `cancelling a draw returns to the exact scroll the medtech left`() {
        setDrawableContent(state(answers = misplacedBox))
        val redraw = sheetNode(VerifyTestTags.REDRAW_BOX)
        val before = redraw.getBoundsInRoot()

        redraw.performClick()
        composeRule.onNodeWithTag(VerifyTestTags.DRAW_CANCEL).performClick()

        assertEquals(before, composeRule.onNodeWithTag(VerifyTestTags.REDRAW_BOX).getBoundsInRoot())
    }

    @Test
    fun `saving a box returns to the top, where the frame and its new box are`() {
        val r = setDrawableContent(state(answers = misplacedBox))
        val frameAtTop = composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).getBoundsInRoot()

        sheetNode(VerifyTestTags.NOTE_FIELD)
        sheetNode(VerifyTestTags.REDRAW_BOX).performClick()
        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).performTouchInput {
            swipe(start = Offset(width * 0.3f, height * 0.3f), end = Offset(width * 0.6f, height * 0.6f))
        }
        composeRule.onNodeWithTag(VerifyTestTags.DRAW_ACCEPT).assertIsEnabled().performClick()

        assertEquals(1, r.drawnBoxes.size)
        assertEquals(frameAtTop, composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW).getBoundsInRoot())
    }

    // Leaving a sample with unsubmitted edits

    @Test
    fun `a held leave asks before anything is lost`() {
        val r = setContent(state().copy(pendingLeave = LeaveIntent.NEXT_SAMPLE))

        dialogNode(VerifyTestTags.LEAVE_DIALOG_CONFIRM).assertIsDisplayed()
        assertEquals(0, r.confirmedLeaves)
    }

    @Test
    fun `confirming the leave dialog reports it`() {
        val r = setContent(state().copy(pendingLeave = LeaveIntent.EXIT))

        dialogNode(VerifyTestTags.LEAVE_DIALOG_CONFIRM).performClick()

        assertEquals(1, r.confirmedLeaves)
        assertEquals(0, r.dismissedLeaves)
    }

    @Test
    fun `keep editing reports the medtech is staying`() {
        val r = setContent(state().copy(pendingLeave = LeaveIntent.PREVIOUS_SAMPLE))

        dialogNode(VerifyTestTags.LEAVE_DIALOG_DISMISS).performClick()

        assertEquals(1, r.dismissedLeaves)
        assertEquals(0, r.confirmedLeaves)
    }

    @Test
    fun `nothing held, nothing asked`() {
        setContent(state())

        composeRule.onNodeWithTag(VerifyTestTags.LEAVE_DIALOG_CONFIRM).assertDoesNotExist()
    }

    private fun noModelOutputState(findings: List<Finding> = emptyList()) = VerificationUiState(
        frame = frame(source = FrameSource.MANUAL, predictions = 0),
        frameIndexInQueue = 1,
        queueSize = 1,
        findings = findings,
    )

    /**
     * Add Species is present on a frame with no model output, and it is the only way to verify one.
     *
     * The species checklist this replaced was a second, parallel set of controls reached only on
     * this kind of frame. Losing this path would silently strand every field a medtech captured
     * during an outage.
     */
    @Test
    fun `a frame with no model output still offers Add Species`() {
        setContent(noModelOutputState())

        sheetNode(VerifyTestTags.ADD_SPECIES).assertIsDisplayed()
    }

    @Test
    fun `a frame with model output offers Add Species too`() {
        setContent(state())

        sheetNode(VerifyTestTags.ADD_SPECIES).assertIsDisplayed()
    }

    @Test
    fun `tapping Add Species reports it`() {
        val r = setContent(noModelOutputState())

        sheetNode(VerifyTestTags.ADD_SPECIES).performClick()

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
            VerifyTestTags.QUESTION_Q1,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.BOXES_TOGGLE).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyTestTags.DETECTION_PREV).assertDoesNotExist()
    }

    @Test
    fun `an added species shows its picker and its field total`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ).copy(expandedFindingIndex = 0),
        )

        sheetNode(VerifyTestTags.addedSpeciesDropdown(0)).assertIsDisplayed()
        sheetNode(VerifyTestTags.countField(0)).assertIsDisplayed()
    }

    /**
     * The disclosure only exists when there is optional work behind it, and its label says how
     * much. A closed disclosure that hides the fact there is anything to do pulls at nobody.
     */
    @Test
    fun `eggs with no box get a disclosure that counts them`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 3,
                            drawnBoxes = listOf(ImageBox(x = 1f, y = 1f, width = 2f, height = 2f)),
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.LOCATE_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithText("Locate eggs · 1 of 3 located").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.drawBox(0, 0)).assertDoesNotExist()
    }

    @Test
    fun `expanding the disclosure offers one draw action per egg, addressed by slot`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 2,
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.LOCATE_TOGGLE).performClick()

        sheetNode(VerifyTestTags.drawBox(0, 1)).performClick()

        // The slot travels with the request. Addressing the row alone is what let two eggs of
        // one species collide on a single box.
        assertEquals(listOf(0 to 1), r.beganDraw)
    }

    /** A species the model's boxes already cover has nothing left to locate. */
    @Test
    fun `a total the boxes already cover shows no disclosure`() {
        setContent(
            state(
                answers = listOf(
                    answered(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
                    VerificationAnswers(
                        species = EggSpecies.ASCARIS,
                        fieldTotal = 1,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.LOCATE_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun `an added species can be removed`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(Finding(answers = VerificationAnswers(fieldTotal = 1))),
            ).copy(expandedFindingIndex = 0),
        )

        sheetNode(VerifyTestTags.removeFinding(0)).performClick()

        assertEquals(listOf(0), r.removedFindings)
    }

    @Test
    fun `remove control is a Discard text link, not a trash icon`() {
        setContent(
            noModelOutputState(
                findings = listOf(Finding(answers = VerificationAnswers(fieldTotal = 1))),
            ).copy(expandedFindingIndex = 0),
        )

        sheetNode(VerifyTestTags.removeFinding(0)).assertTextContains("Discard")
        composeRule.onNodeWithContentDescription("Remove this species").assertDoesNotExist()
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

    // ── the Other species suggestions ────────────────────────────────────────

    /** Q3 unchecked, species OTHER, three characters typed: the state the index answers to. */
    private fun typingOther(vararg names: String) = state(
        answers = listOf(
            VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                speciesConfirmed = false,
                species = EggSpecies.OTHER,
                otherSpeciesText = "fas",
            ),
        ),
    ).copy(
        speciesSuggestions = names.toList(),
        speciesSuggestionTarget = SuggestionTarget.CurrentDetection,
        speciesSuggestionQuery = "fas",
    )

    @Test
    fun `species already on this device are offered under the Other field`() {
        setContent(typingOther("Fasciola hepatica"))

        sheetNode(VerifyTestTags.otherSpeciesSuggestion("Fasciola hepatica")).assertIsDisplayed()
    }

    @Test
    fun `tapping a suggestion fills the field`() {
        val recorder = setContent(typingOther("Fasciola hepatica"))

        sheetNode(VerifyTestTags.otherSpeciesSuggestion("Fasciola hepatica")).performClick()

        // Reported as ordinary text, through the same handler typing uses. Nothing is committed
        // and nothing is locked: the medtech can type straight over it.
        assertEquals(listOf("Fasciola hepatica"), recorder.otherSpecies)
    }

    @Test
    fun `a suggestion identical to what is typed is not offered`() {
        // It answers nothing, and reads as the field failing to notice it has been answered.
        setContent(typingOther("fas"))

        composeRule.onNodeWithTag(VerifyTestTags.OTHER_SPECIES_SUGGESTIONS).assertDoesNotExist()
    }

    @Test
    fun `a list fetched for another row is not offered here`() {
        // The staleness rule, from the rendering side: same names, same text, different owner.
        setContent(
            typingOther("Fasciola hepatica")
                .copy(speciesSuggestionTarget = SuggestionTarget.AddedFinding(1)),
        )

        composeRule.onNodeWithTag(VerifyTestTags.OTHER_SPECIES_SUGGESTIONS).assertDoesNotExist()
    }

    @Test
    fun `compact card shows species name and egg count and has no dropdown`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ),
        )

        sheetNode(VerifyTestTags.addedSpeciesSummary(0)).assertIsDisplayed()
        composeRule.onNodeWithText("Ascaris lumbricoides").assertIsDisplayed()
        composeRule.onNodeWithText("1 egg").assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.addedSpeciesDropdown(0)).assertDoesNotExist()
    }

    @Test
    fun `tapping compact card triggers onExpandFinding`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1)),
                    Finding(answers = VerificationAnswers(species = EggSpecies.TRICHURIS, fieldTotal = 2)),
                ),
            ),
        )

        sheetNode(VerifyTestTags.addedSpeciesSummary(1)).performClick()

        assertEquals(listOf(1), r.expandedFindings)
    }

    @Test
    fun `tapping outside expanded card triggers onCollapseFinding while tapping count field does not`() {
        val r = setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1)),
                ),
            ).copy(expandedFindingIndex = 0),
        )

        sheetNode(VerifyTestTags.countField(0)).performClick()
        assertEquals(emptyList<Int>(), r.collapsedFindings)

        sheetNode(VerifyTestTags.MODEL_OUTPUT_PANEL).performClick()
        assertEquals(listOf(0), r.collapsedFindings)
    }

    @Test
    fun `unfinished summary displays warning and not chosen label`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(answers = VerificationAnswers(fieldTotal = 0)),
                ),
            ),
        )

        sheetNode(VerifyTestTags.addedSpeciesSummary(0)).assertIsDisplayed()
        composeRule.onNodeWithText("Species not chosen").assertIsDisplayed()
        composeRule.onNodeWithText("Not finished. Tap to complete.").assertIsDisplayed()
    }

    @Test
    fun `remove button is positioned at lower right beside the count field`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1)),
                ),
            ).copy(expandedFindingIndex = 0),
        )

        val countBounds = sheetNode(VerifyTestTags.countField(0)).getBoundsInRoot()
        val removeBounds = sheetNode(VerifyTestTags.removeFinding(0)).getBoundsInRoot()

        assertTrue(
            "Remove left edge (${removeBounds.left}) should be >= count field right edge (${countBounds.right})",
            removeBounds.left >= countBounds.right,
        )
        assertTrue(
            "Remove top edge (${removeBounds.top}) should be >= count field top edge (${countBounds.top})",
            removeBounds.top >= countBounds.top,
        )
    }

    @Test
    fun `will be saved section does not exist`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1)),
                ),
            ),
        )

        composeRule.onNodeWithText("WILL BE SAVED").assertDoesNotExist()
    }

    // ── the stage line on the compact summary card ──────────────────────────

    @Test
    fun `stage shows below the name on the compact summary card`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            stage = EggStage.CORTICATED_FERTILIZED,
                            fieldTotal = 3,
                        ),
                    ),
                ),
            ),
        )

        val nameBounds = composeRule
            .onNodeWithTag(VerifyTestTags.addedSpeciesSummaryName(0), useUnmergedTree = true)
            .getBoundsInRoot()
        val stageBounds = composeRule
            .onNodeWithTag(VerifyTestTags.addedSpeciesSummaryStage(0), useUnmergedTree = true)
            .getBoundsInRoot()

        composeRule.onNodeWithText("Corticated Fertilized", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            "Stage top (${stageBounds.top}) should be at or below name bottom (${nameBounds.bottom})",
            stageBounds.top >= nameBounds.bottom,
        )
    }

    @Test
    fun `count centers on the whole name-plus-stage block, not just the name line`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            stage = EggStage.CORTICATED_FERTILIZED,
                            fieldTotal = 3,
                        ),
                    ),
                ),
            ),
        )

        val textBounds = composeRule
            .onNodeWithTag(VerifyTestTags.addedSpeciesSummaryText(0), useUnmergedTree = true)
            .getBoundsInRoot()
        val nameBounds = composeRule
            .onNodeWithTag(VerifyTestTags.addedSpeciesSummaryName(0), useUnmergedTree = true)
            .getBoundsInRoot()
        val countBounds = composeRule
            .onNodeWithTag(VerifyTestTags.addedSpeciesSummaryCount(0), useUnmergedTree = true)
            .getBoundsInRoot()

        val textCenter = (textBounds.top + textBounds.bottom) / 2f
        val countCenter = (countBounds.top + countBounds.bottom) / 2f
        val nameCenter = (nameBounds.top + nameBounds.bottom) / 2f
        val tolerance = 1.dp
        val centerGap = if (countCenter > textCenter) countCenter - textCenter else textCenter - countCenter

        assertTrue(
            "Count center ($countCenter) should align with the whole block's center ($textCenter)",
            centerGap <= tolerance,
        )
        assertTrue(
            "Count center ($countCenter) should be clearly below the name line's own center " +
                "($nameCenter), proving it is not centered on line 1 alone",
            countCenter > nameCenter + tolerance,
        )
    }

    @Test
    fun `a custom OTHER stage shows its trimmed text`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            stage = EggStage.OTHER,
                            otherStageText = "Embryonated",
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Embryonated", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `no stage means no second line, name and count still shown`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.ASCARIS,
                            stage = null,
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Ascaris lumbricoides", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1 egg", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyTestTags.addedSpeciesSummaryStage(0), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a leftover stage never shows when the species is Other`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(
                            species = EggSpecies.OTHER,
                            otherSpeciesText = "Taenia",
                            stage = EggStage.CORTICATED_FERTILIZED,
                            fieldTotal = 1,
                        ),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.addedSpeciesSummaryStage(0), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `expanding an added species keeps its stage dropdown, collapsing hides it`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1),
                    ),
                ),
            ).copy(expandedFindingIndex = 0),
        )

        sheetNode(VerifyTestTags.addedStageDropdown(0)).assertIsDisplayed()
    }

    @Test
    fun `a collapsed added species card has no stage dropdown`() {
        setContent(
            noModelOutputState(
                findings = listOf(
                    Finding(
                        answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 1),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(VerifyTestTags.addedStageDropdown(0)).assertDoesNotExist()
    }
}
