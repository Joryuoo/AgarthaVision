package com.agarthavision.ui.verify

import app.cash.turbine.test
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storeState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(storeState)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()

    private fun viewModel() = VerificationViewModel(flaggedFrameStore, submitVerificationUseCase)

    private fun makeFrame(predictions: Int = 2): FlaggedFrame {
        val preds = List(predictions) {
            Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)
        }
        return FlaggedFrame(
            sessionId = "session-1",
            capturedAt = Instant.EPOCH,
            jpegBytes = ByteArray(4),
            predictions = preds,
        )
    }

    private fun makeFrameWithId(id: Int, predictions: Int = 1): FlaggedFrame {
        // Distinct sampleId so equals/hashCode see each frame as unique
        val preds = List(predictions) {
            Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)
        }
        return FlaggedFrame(
            sampleId = "sample-$id",
            sessionId = "session-1",
            capturedAt = Instant.ofEpochMilli(id.toLong()),
            jpegBytes = ByteArray(4),
            predictions = preds,
        )
    }

    private fun makeManualFrame(sampleId: String) =
        makeIdentifiedFrame(sampleId).copy(source = FrameSource.MANUAL, predictions = emptyList())

    /** A frame with an explicit sample id, for assertions that turn on queue position. */
    private fun makeIdentifiedFrame(sampleId: String, predictions: Int = 1) = FlaggedFrame(
        sampleId = sampleId,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = List(predictions) { Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f) },
    )

    @Test
    fun `setFrame initialises answers list matching prediction count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 3)
            vm.setFrame(frame)
            advanceUntilIdle()
            assertEquals(3, vm.state.value.findings.size)
        }

    @Test
    fun `answers persist independently across detections`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 2)
            vm.setFrame(frame)

            vm.onQ1Selected(true)
            vm.onDetectionNext()
            vm.onQ1Selected(false)
            advanceUntilIdle()

            assertEquals(true, vm.state.value.findings[0].answers.isEgg)
            assertEquals(false, vm.state.value.findings[1].answers.isEgg)
        }

    @Test
    fun `onCancel emits Dismiss without removing frame from store`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame())

            vm.events.test {
                vm.onCancel()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
            verify(flaggedFrameStore, never()).remove(any())
        }

    @Test
    fun `successful submit removes frame and emits Dismiss`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success("sample-1"))
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)

            // Fill in complete answers so canSubmit is true
            vm.onQ1Selected(false) // FALSE_POSITIVE — complete after Q1=No

            vm.events.test {
                vm.onSubmit()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun `canSubmit is false when answers are incomplete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            advanceUntilIdle()
            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `canSubmit is true when all answers are complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            vm.onQ1Selected(false) // complete after Q1=No
            advanceUntilIdle()
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `prev and next detection clamp to valid range`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 2))

            vm.onDetectionPrev()
            advanceUntilIdle()
            assertEquals(0, vm.state.value.currentDetectionIndex)

            vm.onDetectionNext()
            vm.onDetectionNext()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.currentDetectionIndex)
        }

    @Test
    fun `queueSize tracks store state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            storeState.value = List(3) { makeFrame() }
            advanceUntilIdle()
            assertEquals(3, vm.state.value.queueSize)
        }

    @Test
    fun `submit failure keeps isSubmitting false and emits ShowError`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.failure(RuntimeException("DB error")))
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            vm.onQ1Selected(false)

            vm.events.test {
                vm.onSubmit()
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is VerificationEvent.ShowError)
                assertEquals("DB error", (event as VerificationEvent.ShowError).message)
            }
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun `onFrameNext advances currentFrame within queue`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frameA = makeFrameWithId(1)
            val frameB = makeFrameWithId(2)
            storeState.value = listOf(frameA, frameB)
            val vm = viewModel()
            vm.setFrame(frameA)
            advanceUntilIdle()

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals(frameB, vm.state.value.frame)
        }

    @Test
    fun `onFrameNext clamps at last frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frameA = makeFrameWithId(1)
            val frameB = makeFrameWithId(2)
            storeState.value = listOf(frameA, frameB)
            val vm = viewModel()
            vm.setFrame(frameB)
            advanceUntilIdle()

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals(frameB, vm.state.value.frame)
        }

    @Test
    fun `onFramePrev clamps at first frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frameA = makeFrameWithId(1)
            val frameB = makeFrameWithId(2)
            storeState.value = listOf(frameA, frameB)
            val vm = viewModel()
            vm.setFrame(frameA)
            advanceUntilIdle()

            vm.onFramePrev()
            advanceUntilIdle()

            assertEquals(frameA, vm.state.value.frame)
        }

    @Test
    fun `onDeleteFrame removes current frame and advances to next`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frameA = makeFrameWithId(1)
            val frameB = makeFrameWithId(2)
            storeState.value = listOf(frameA, frameB)
            val vm = viewModel()
            vm.setFrame(frameA)
            advanceUntilIdle()

            vm.onDeleteFrame()
            advanceUntilIdle()

            verify(flaggedFrameStore).remove(frameA)
            assertEquals(frameB, vm.state.value.frame)
        }

    @Test
    fun `onDeleteFrame emits Dismiss when queue becomes empty`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val onlyFrame = makeFrameWithId(1)
            storeState.value = listOf(onlyFrame)
            val vm = viewModel()
            vm.setFrame(onlyFrame)
            advanceUntilIdle()

            vm.events.test {
                vm.onDeleteFrame()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
            verify(flaggedFrameStore).remove(onlyFrame)
        }

    @Test
    fun `frameIndexInQueue advances with onFrameNext`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b", "c").map { makeIdentifiedFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[0])
            advanceUntilIdle()
            assertEquals(1, vm.state.value.frameIndexInQueue)

            vm.onFrameNext()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.frameIndexInQueue)

            vm.onFrameNext()
            advanceUntilIdle()
            assertEquals(3, vm.state.value.frameIndexInQueue)
        }

    @Test
    fun `frameIndexInQueue retreats with onFramePrev`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b", "c").map { makeIdentifiedFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[2])
            advanceUntilIdle()
            assertEquals(3, vm.state.value.frameIndexInQueue)

            vm.onFramePrev()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.frameIndexInQueue)
        }

    @Test
    fun `canGoPrev is false on the first frame and canGoNext false on the last`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b").map { makeIdentifiedFrame(it) }
            storeState.value = frames
            val vm = viewModel()

            vm.setFrame(frames[0])
            advanceUntilIdle()
            assertFalse(vm.state.value.canGoPrev)
            assertTrue(vm.state.value.canGoNext)

            vm.setFrame(frames[1])
            advanceUntilIdle()
            assertTrue(vm.state.value.canGoPrev)
            assertFalse(vm.state.value.canGoNext)
        }

    @Test
    fun `setFrame recomputes position without waiting for a store emission`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The regression: only the init collector used to write frameIndexInQueue, so
            // paging the queue left the counter pinned to whatever the last emission saw.
            val frames = listOf("a", "b", "c").map { makeIdentifiedFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[0])
            advanceUntilIdle()

            // No further store emission from here on.
            vm.setFrame(frames[2])
            advanceUntilIdle()

            assertEquals(3, vm.state.value.frameIndexInQueue)
            assertEquals(3, vm.state.value.queueSize)
        }

    @Test
    fun `a store emission that changed a frame is not conflated away`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The regression: FlaggedFrame.equals compared sampleId alone, so a Room
            // re-emission carrying a changed frame compared equal to the list already held,
            // StateFlow conflated it, and the queue silently went stale. The equality contract
            // itself lives in FlaggedFrameTest; this asserts the collector still sees through.
            val frame = makeIdentifiedFrame("a")
            storeState.value = listOf(frame)
            val vm = viewModel()
            vm.setFrame(frame)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.queueSize)

            storeState.value = listOf(frame, makeIdentifiedFrame("b"))
            advanceUntilIdle()

            assertEquals(2, vm.state.value.queueSize)
        }

    @Test
    fun `frame cycling pages onto manual frames too`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // This used to skip them, because manual captures had their own sheet and the host
            // picked a sheet from the frame it opened with and never re-evaluated - so paging
            // onto one rendered the wrong screen. There is one screen now, so the cycle is the
            // whole queue and a manual frame is simply a frame with no model output.
            val ai1 = makeIdentifiedFrame("ai-1")
            val manual = makeManualFrame("manual-1")
            val ai2 = makeIdentifiedFrame("ai-2")
            storeState.value = listOf(ai1, manual, ai2)
            val vm = viewModel()
            vm.setFrame(ai1)
            advanceUntilIdle()

            assertEquals(3, vm.state.value.queueSize)
            assertEquals(1, vm.state.value.frameIndexInQueue)

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals(manual, vm.state.value.frame)
            assertEquals(2, vm.state.value.frameIndexInQueue)
            assertTrue(vm.state.value.canGoNext)
        }

    @Test
    fun `a manual frame opens with one finding and no box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // No prediction means the isEgg / isBoxCorrect questions never render, and the
            // medtech names a species and a count directly - the old ManualSheet flow.
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            advanceUntilIdle()

            val findings = vm.state.value.findings
            assertEquals(1, findings.size)
            assertNull(findings[0].prediction)
        }

    @Test
    fun `queueSize counts both sources`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            storeState.value = listOf(
                makeIdentifiedFrame("ai-1"),
                makeManualFrame("manual-1"),
                makeManualFrame("manual-2"),
            )
            val vm = viewModel()
            vm.setFrame(makeIdentifiedFrame("ai-1"))
            advanceUntilIdle()

            assertEquals(3, vm.state.value.queueSize)
        }

    @Test
    fun `a frame that leaves the queue stays on screen but loses its position`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // This used to be reached by marking the open frame repeat. Repeat is gone, but the
            // sentinel still earns its keep: the frame can leave the queue underneath the
            // medtech - deleted from the queue screen, or tombstoned - and showing "Frame 0/2"
            // was the bug it exists to prevent.
            val open = makeIdentifiedFrame("ai-1")
            val other = makeIdentifiedFrame("ai-2")
            storeState.value = listOf(open, other)
            val vm = viewModel()
            vm.setFrame(open)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.frameIndexInQueue)
            assertTrue(vm.state.value.canGoNext)

            storeState.value = listOf(other)
            advanceUntilIdle()

            // Still showing it, but with no position and both frame buttons disabled, so it
            // cannot page from a frame that is not there.
            assertEquals(open, vm.state.value.frame)
            assertEquals(0, vm.state.value.frameIndexInQueue)
            assertFalse(vm.state.value.canGoPrev)
            assertFalse(vm.state.value.canGoNext)
        }

    @Test
    fun `onStageSelected updates the current answer's stage`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onStageSelected(EggStage.UNFERTILIZED)
            advanceUntilIdle()

            assertEquals(EggStage.UNFERTILIZED, vm.state.value.findings[0].answers.stage)
        }

    @Test
    fun `onSpeciesSelected resets stage to null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onStageSelected(EggStage.UNFERTILIZED)

            vm.onSpeciesSelected(EggSpecies.TRICHURIS)
            advanceUntilIdle()

            assertEquals(null, vm.state.value.findings[0].answers.stage)
        }

    @Test
    fun `onQ1Selected resets stage to null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onStageSelected(EggStage.UNFERTILIZED)

            vm.onQ1Selected(true)
            advanceUntilIdle()

            assertEquals(null, vm.state.value.findings[0].answers.stage)
        }

    @Test
    fun `onQ2Selected resets stage to null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onStageSelected(EggStage.UNFERTILIZED)

            vm.onQ2Selected(true)
            advanceUntilIdle()

            assertEquals(null, vm.state.value.findings[0].answers.stage)
        }

    // Polyparasitism: several species on one frame (86d4ab4tq)

    @Test
    fun `adding a species appends a finding with no prediction`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onAddFinding()
            advanceUntilIdle()

            val findings = vm.state.value.findings
            assertEquals(2, findings.size)
            assertNotNull("The model box keeps its prediction.", findings[0].prediction)
            assertNull("An added species has no box behind it.", findings[1].prediction)
        }

    @Test
    fun `an added species is removable`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddFinding()

            vm.onRemoveFinding(1)
            advanceUntilIdle()

            assertEquals(1, vm.state.value.findings.size)
        }

    @Test
    fun `a model box cannot be removed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Constraint C8: the way to reject a box is to answer "not an egg", which keeps it
            // as a labelled FALSE_POSITIVE row. Deleting it would drop it from the corpus.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 2))

            vm.onRemoveFinding(0)
            advanceUntilIdle()

            assertEquals(2, vm.state.value.findings.size)
            assertNotNull(vm.state.value.findings[0].prediction)
        }

    @Test
    fun `a typed egg count lands on the added finding`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddFinding()

            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            vm.onAddedStageSelected(1, EggStage.LARVATED)
            vm.onEggCountChanged(1, "4")
            advanceUntilIdle()

            val added = vm.state.value.findings[1].answers
            assertEquals(EggSpecies.HOOKWORM, added.species)
            assertEquals(EggStage.LARVATED, added.stage)
            assertEquals(4, added.eggCount)
            assertEquals(true, added.speciesTouched)
        }

    @Test
    fun `a non-numeric egg count clears rather than crashing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddFinding()
            vm.onEggCountChanged(1, "4")

            vm.onEggCountChanged(1, "abc")
            advanceUntilIdle()

            assertNull(vm.state.value.findings[1].answers.eggCount)
        }

    @Test
    fun `re-picking the species already showing still counts as touched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Once the field is pre-filled from the model, this re-pick is the only signal
            // separating "I agree" from "I never looked" - and detections is the corpus.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onQ1Selected(true)
            vm.onQ2Selected(true)

            vm.onSpeciesSelected(EggSpecies.ASCARIS)
            vm.onSpeciesSelected(EggSpecies.ASCARIS)
            advanceUntilIdle()

            assertEquals(true, vm.state.value.findings[0].answers.speciesTouched)
        }


    // Auto-fill from the model output, and the provenance it needs (86d4ab4tq)

    @Test
    fun `a box is pre-filled with the class the model gave it, untouched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertFalse(
                "A pre-fill is not a human answer - submitting it untouched must not read " +
                    "as a confirmation.",
                answers.speciesTouched,
            )
        }

    @Test
    fun `answering the box questions keeps the pre-filled species`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The reset that clears later answers used to clear to empty, which threw the
            // pre-fill away the moment the medtech answered Q1 - i.e. always.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onQ1Selected(true)
            vm.onQ2Selected(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertFalse(answers.speciesTouched)
        }

    @Test
    fun `changing the species away and back still reads as touched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onQ1Selected(true)
            vm.onQ2Selected(true)

            vm.onSpeciesSelected(EggSpecies.TRICHURIS)
            vm.onSpeciesSelected(EggSpecies.ASCARIS)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertTrue("Landing back on the model's answer deliberately is still a choice.", answers.speciesTouched)
        }

    @Test
    fun `an unrecognised model class pre-fills nothing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Guessing OTHER would be wrong: OTHER carries a free-text box only a human can
            // fill, so it would look answered while being incomplete.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1).copy(
                predictions = listOf(Prediction("Schistosoma", 0.8f, 1f, 2f, 3f, 4f)),
            ))
            advanceUntilIdle()

            assertNull(vm.state.value.findings[0].answers.species)
        }

}
