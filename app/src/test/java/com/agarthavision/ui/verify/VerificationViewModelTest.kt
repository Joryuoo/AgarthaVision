package com.agarthavision.ui.verify

import app.cash.turbine.test
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
// One subject, one fixture. Splitting by concern would duplicate the ViewModel setup across
// files and make the question-chain cases harder to read against each other.
@Suppress("LargeClass")
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

    /** A frame whose model class this app cannot map to an EggSpecies, so nothing seeds. */
    private fun unmappableFrame() = makeFrame(predictions = 1).copy(
        predictions = listOf(Prediction("Schistosoma", 0.8f, 1f, 2f, 3f, 4f)),
    )

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

    /**
     * The governing principle of the screen, stated as a test.
     *
     * Every answer opens pre-filled from model output, so a medtech whose model was right
     * submits without typing or tapping anything. Ten fields a smear, most of them correct: the
     * taps this saves are the whole economics of the change.
     */
    @Test
    fun `a frame the model got right submits with zero taps`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 3))
            advanceUntilIdle()

            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `canSubmit is false while a row has no species the app can pre-fill`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // An unmappable model class seeds no species, so the row is genuinely unanswered.
            val vm = viewModel()
            vm.setFrame(unmappableFrame())
            advanceUntilIdle()

            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `canSubmit is true when all answers are complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(unmappableFrame())
            vm.onQ1Selected(false) // complete after Q1=No
            advanceUntilIdle()
            assertTrue(vm.state.value.canSubmit)
        }

    /**
     * A clean field is a real negative result and the most common one in surveillance. It has
     * nothing to fill in, so it must be recordable as it stands.
     */
    @Test
    fun `a clean field submits as it stands`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 0))
            advanceUntilIdle()

            assertTrue(vm.state.value.findings.isEmpty())
            assertTrue(vm.state.value.canSubmit)
        }

    // Confirming the suggested species

    @Test
    fun `agreeing with the suggested species records it as the answer and completes the detection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onQ1Selected(true)
            vm.onQ2Selected(true)

            vm.onSpeciesConfirmed(true)
            advanceUntilIdle()

            val answer = vm.state.value.findings[0].answers
            assertEquals(true, answer.speciesConfirmed)
            assertEquals(EggSpecies.ASCARIS, answer.species)
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `rejecting the suggested species clears it and waits for a pick`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onQ1Selected(true)
            vm.onQ2Selected(true)
            vm.onSpeciesConfirmed(true)

            vm.onSpeciesConfirmed(false)
            advanceUntilIdle()

            val answer = vm.state.value.findings[0].answers
            assertEquals(false, answer.speciesConfirmed)
            assertEquals(null, answer.species)
            assertFalse(vm.state.value.canSubmit)

            vm.onSpeciesSelected(EggSpecies.HOOKWORM)
            advanceUntilIdle()
            assertEquals(EggSpecies.HOOKWORM, vm.state.value.findings[0].answers.species)
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `changing an earlier answer resets the species confirmation`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onSpeciesConfirmed(true)

            // A genuine change: the box is in the wrong place after all.
            vm.onQ2Selected(false)
            advanceUntilIdle()
            assertEquals(null, vm.state.value.findings[0].answers.speciesConfirmed)
            assertEquals(null, vm.state.value.findings[0].answers.species)
        }

    /**
     * Re-affirming an answer a row already holds changes nothing.
     *
     * Without this, a stray tap on a pre-filled "Yes" would wipe the pre-filled species beneath
     * it - clearing later answers is correct when an earlier one *changes*, and this is not a
     * change. It would hand the medtech back exactly the work the pre-fill saved them.
     */
    @Test
    fun `re-affirming a pre-filled answer keeps the species under it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onQ1Selected(true)
            vm.onQ2Selected(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertEquals(true, answers.speciesConfirmed)
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

    // Polyparasitism: several species on one frame (86d4ab4tq)

    @Test
    fun `adding a species appends a finding with no prediction`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onAddSpecies()
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
            vm.onAddSpecies()

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
    fun `a typed field total lands on the added species`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddSpecies()

            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            vm.onFieldTotalChanged(1, "4")
            advanceUntilIdle()

            val added = vm.state.value.findings[1].answers
            assertEquals(EggSpecies.HOOKWORM, added.species)
            assertEquals(4, added.fieldTotal)
            assertEquals(true, added.speciesTouched)
        }

    @Test
    fun `a non-numeric field total clears rather than crashing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddSpecies()
            vm.onFieldTotalChanged(1, "4")

            vm.onFieldTotalChanged(1, "abc")
            advanceUntilIdle()

            assertNull(vm.state.value.findings[1].answers.fieldTotal)
        }

    @Test
    fun `the total is taken as typed, not clamped up to the floor`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The floor is usually above the first digit of the number being typed - heading
            // for 23 against nine boxed eggs, a clamp would snap "2" to 9 and the 3 would land
            // on the wrong number. Submit holds instead, and says why.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 2))
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(2, EggSpecies.ASCARIS)

            vm.onFieldTotalChanged(2, "1")
            advanceUntilIdle()

            assertEquals(1, vm.state.value.findings[2].answers.fieldTotal)
            assertFalse(
                "One egg is fewer than the two boxes already kept for this species.",
                vm.state.value.canSubmit,
            )

            vm.onFieldTotalChanged(2, "12")
            advanceUntilIdle()
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `naming a species another card already holds merges the two`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // sample_species_findings is unique on (sample_id, species) and the detection ids
            // derive from the species, so two cards naming one species have nowhere separate to
            // be stored - and the reopen path brought them back merged anyway.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 0))
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.HOOKWORM)
            vm.onFieldTotalChanged(0, "3")

            vm.onAddSpecies()
            vm.onFieldTotalChanged(1, "2")
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            advanceUntilIdle()

            val findings = vm.state.value.findings
            assertEquals("The two cards became one.", 1, findings.size)
            assertEquals(5, findings[0].answers.fieldTotal)
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


    // Where the species comes from, and the provenance it needs (86d4ab4tq / 86d4auj84)

    /**
     * A box opens fully pre-filled, and **untouched**.
     *
     * Both halves matter. The pre-fill is what lets a correct model cost zero taps; the untouched
     * flag is what keeps that from entering the retraining corpus as a human judgement. A seeded
     * species is the model's own answer sitting in the slot a human answer is read from, and
     * `species_touched` is the only thing that tells the two apart.
     */
    @Test
    fun `a box opens pre-filled from the model, and untouched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(true, answers.isEgg)
            assertEquals(true, answers.isBoxCorrect)
            assertEquals(true, answers.speciesConfirmed)
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertFalse("Nobody has agreed with this yet.", answers.speciesTouched)
        }

    @Test
    fun `confirming a pre-filled species is what marks it touched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            assertFalse(vm.state.value.findings[0].answers.speciesTouched)

            vm.onSpeciesConfirmed(true)
            advanceUntilIdle()

            assertTrue(vm.state.value.findings[0].answers.speciesTouched)
        }

    @Test
    fun `confirming the model's species records it as a human answer`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onQ1Selected(true)
            vm.onQ2Selected(true)

            vm.onSpeciesConfirmed(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertTrue(
                "A yes is a deliberate assertion: the medtech read the suggestion and agreed " +
                    "with it. detections doubles as the retraining corpus, so a species with " +
                    "no human behind it must never look like one with.",
                answers.speciesTouched,
            )
        }

    @Test
    fun `changing an earlier answer clears the species rather than re-seeding it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Re-seeding from the model here would answer the confirm question on the medtech's
            // behalf a second time, after they have told the screen the box is wrong.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onSpeciesConfirmed(true)

            vm.onQ1Selected(false)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(null, answers.species)
            assertEquals(null, answers.speciesConfirmed)
            assertFalse(answers.speciesTouched)
        }

    @Test
    fun `changing the species away and back still reads as touched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onSpeciesSelected(EggSpecies.TRICHURIS)
            vm.onSpeciesSelected(EggSpecies.ASCARIS)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(EggSpecies.ASCARIS, answers.species)
            assertTrue("Landing back on the model's answer deliberately is still a choice.", answers.speciesTouched)
        }

    @Test
    fun `an unrecognised model class pre-fills no species, but still pre-fills the box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Guessing OTHER would be wrong: OTHER carries a free-text box only a human can
            // fill, so it would look answered while being incomplete. The box questions are
            // still pre-filled - the model did draw a box, whatever it called what is in it.
            val vm = viewModel()
            vm.setFrame(unmappableFrame())
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertNull(answers.species)
            assertNull("Nothing to confirm, so the picker is offered directly.", answers.speciesConfirmed)
            assertEquals(true, answers.isEgg)
            assertEquals(true, answers.isBoxCorrect)
        }

    // Q4, derived rather than asked

    @Test
    fun `a frame the medtech added nothing to did not miss an egg`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 2))
            advanceUntilIdle()

            assertEquals(false, vm.state.value.missedEgg)
        }

    @Test
    fun `a species the boxes already account for is not a miss`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // One Ascaris boxed, and the medtech says there is one Ascaris in the field. They
            // agree, so nothing was missed - the row exists but claims no unboxed egg.
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.ASCARIS)
            vm.onFieldTotalChanged(1, "1")
            advanceUntilIdle()

            assertEquals(false, vm.state.value.missedEgg)
        }

    @Test
    fun `adding an egg the model never boxed is what says it missed one`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            advanceUntilIdle()

            assertEquals(true, vm.state.value.missedEgg)
        }

    @Test
    fun `taking the added egg away again says it did not`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            assertEquals(true, vm.state.value.missedEgg)

            vm.onRemoveFinding(1)
            advanceUntilIdle()

            assertEquals(false, vm.state.value.missedEgg)
        }

    /**
     * The case a total comparison gets wrong.
     *
     * Rejecting one of the model's boxes and adding an egg it missed nets out to the same egg
     * count, while both things are true. Q4 is read off the added rows rather than off the
     * totals precisely so this does not silently report that nothing was missed.
     */
    @Test
    fun `a rejected box and an added egg still report a miss`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 1))

            vm.onQ1Selected(false)
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.HOOKWORM)
            advanceUntilIdle()

            assertEquals(true, vm.state.value.missedEgg)
        }

    /**
     * There is no model claim on a frame captured with the container unreachable, so there is
     * nothing for it to have missed. Null, not false: `samples.needs_reannotation` is nullable
     * for exactly this, and false would assert something about a model that never ran.
     */
    @Test
    fun `a frame with no model output has no missed-egg answer at all`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("sample-manual"))
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.ASCARIS)
            advanceUntilIdle()

            assertNull(vm.state.value.missedEgg)
        }


}
