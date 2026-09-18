package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Drawing a box, and the one rule that must hold afterwards.
 *
 * **Once a box has been replaced, Q2 is "No" and cannot be set back to "Yes".** The model did put
 * the box in the wrong place; that fact does not stop being true because a human fixed it, and it
 * is precisely the training signal the whole feature exists to capture. An implementation that
 * lets Q2 flip back leaves a detection claiming correct localisation while carrying the human's
 * geometry — a destroyed label, with nothing failing anywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoxRedrawTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frames = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(frames)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()

    private fun viewModel() = VerificationViewModel(flaggedFrameStore, submitVerificationUseCase)

    private fun frame(predictions: Int = 1) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = List(predictions) { Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f) },
        imageWidth = 640,
        imageHeight = 640,
    )

    private val drawn = ImageBox(x = 300f, y = 220f, width = 80f, height = 60f)

    @Test
    fun `nobody is drawing until a draw is begun`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            advanceUntilIdle()

            assertFalse(vm.state.value.isDrawing)
            assertNull(vm.state.value.drawTarget)
        }

    @Test
    fun `committing a redraw records the box and says the model placed it wrong`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onQ2Selected(false)

            vm.onBeginDraw(0, null)
            vm.onBoxDrawn(drawn)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(drawn, answers.drawnBox)
            assertEquals(false, answers.isBoxCorrect)
            assertTrue(answers.boxReplaced)
            assertFalse("The gesture is over.", vm.state.value.isDrawing)
        }

    /**
     * The rule, stated directly. This is the assertion to keep if any other in this file goes.
     */
    @Test
    fun `Q2 cannot be set back to Yes once a box has been replaced`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onQ2Selected(false)
            vm.onBeginDraw(0, null)
            vm.onBoxDrawn(drawn)

            vm.onQ2Selected(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(false, answers.isBoxCorrect)
            assertTrue(answers.boxReplaced)
            assertEquals("And the human's geometry survives the attempt.", drawn, answers.drawnBox)
        }

    /**
     * Redrawing sets Q2 on the medtech's behalf, so it works even from a pre-filled "Yes".
     *
     * The chain only offers the affordance after a "No", but the action must not depend on the
     * order the two happened in — a caller reaching it another way still has to end up with a
     * localisation error recorded, not a correct box holding someone else's coordinates.
     */
    @Test
    fun `a redraw from a pre-filled Yes still records the localisation error`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            assertEquals(true, vm.state.value.findings[0].answers.isBoxCorrect)

            vm.onBeginDraw(0, null)
            vm.onBoxDrawn(drawn)
            advanceUntilIdle()

            assertEquals(false, vm.state.value.findings[0].answers.isBoxCorrect)
        }

    /**
     * An added egg has no model box to have been wrong about, so drawing one asserts nothing
     * about Q2. Writing false there would record a localisation error against a model that never
     * localised anything.
     */
    @Test
    fun `locating an added egg leaves Q2 alone`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)

            vm.onBeginDraw(1, 0)
            vm.onBoxDrawn(drawn)
            advanceUntilIdle()

            val added = vm.state.value.findings[1].answers
            assertEquals(listOf(drawn), added.drawnBoxes)
            assertNull("There was never a model box here to be wrong about.", added.isBoxCorrect)
            assertFalse(added.boxReplaced)
            assertNull("And the replacement slot stays empty.", added.drawnBox)
        }

    /**
     * **The regression the species-first rewrite exists for.**
     *
     * Two eggs of one species used to be two findings sharing one derived detection id, so the
     * second box replaced the first on submit — silently, with no error anywhere. They are now
     * two slots under one card, and both boxes have to survive.
     */
    @Test
    fun `two eggs of one species keep two separate boxes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val second = ImageBox(x = 40f, y = 40f, width = 10f, height = 10f)
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            vm.onFieldTotalChanged(1, "2")

            vm.onBeginDraw(1, 0)
            vm.onBoxDrawn(drawn)
            vm.onBeginDraw(1, 1)
            vm.onBoxDrawn(second)
            advanceUntilIdle()

            assertEquals(listOf(drawn, second), vm.state.value.findings[1].answers.drawnBoxes)
        }

    @Test
    fun `a slot beyond the eggs that species claims cannot be drawn for`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // One unboxed egg means one slot. Slot 1 does not exist, and drawing into it would
            // write geometry the mapper never emits a row for.
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)

            vm.onBeginDraw(1, 1)
            advanceUntilIdle()

            assertFalse(vm.state.value.isDrawing)
        }

    @Test
    fun `an added species with no box is still complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            advanceUntilIdle()

            assertTrue(vm.state.value.findings[1].answers.drawnBoxes.isEmpty())
            assertTrue(vm.state.value.findings[1].isComplete)
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `cancelling a draw leaves the row exactly as it was`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onQ2Selected(false)
            val before = vm.state.value.findings[0].answers

            vm.onBeginDraw(0, null)
            vm.onCancelDraw()
            advanceUntilIdle()

            assertEquals(before, vm.state.value.findings[0].answers)
            assertFalse(vm.state.value.isDrawing)
        }

    /**
     * Changing an earlier answer clears the later ones, but geometry is not an answer to any of
     * them. A medtech who redrew a box and then changed their mind about the species has not
     * un-drawn the box, and dropping it would quietly restore the model's own coordinates.
     */
    @Test
    fun `changing an earlier answer does not un-draw the box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onQ2Selected(false)
            vm.onBeginDraw(0, null)
            vm.onBoxDrawn(drawn)

            vm.onQ1Selected(false)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertNull("The species did go.", answers.species)
            assertEquals(drawn, answers.drawnBox)
            assertTrue(answers.boxReplaced)
        }

    @Test
    fun `a draw cannot be begun for a row that does not exist`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onBeginDraw(7, null)
            advanceUntilIdle()

            assertFalse(vm.state.value.isDrawing)
        }

    @Test
    fun `a drawn box arrives with nobody drawing and is ignored`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onBoxDrawn(drawn)
            advanceUntilIdle()

            assertNull(vm.state.value.findings[0].answers.drawnBox)
        }
}
