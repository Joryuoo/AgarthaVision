package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.inference.CancelInferenceUseCase
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * A sample opened while its model output is still pending (14zcqntj6p1).
 *
 * Capture no longer waits on a model, so the medtech can open a frame the queue has not reached.
 * What must hold: annotation is locked while it waits, cancelling is confirmed and then final, a
 * result landing while the screen is open replaces the indicator in place, and a result landing
 * after a confirmed cancel never appears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationPendingInferenceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frames = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(frames)
    }
    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase = mock {
        onBlocking { invoke(any()) } doReturn Result.success(emptyList())
    }
    private val cancelInference: CancelInferenceUseCase = mock()

    private fun viewModel() = VerificationViewModel(
        flaggedFrameStore,
        mock<SubmitVerificationUseCase>(),
        searchSpeciesSuggestions,
        cancelInference,
    )

    private fun pending(state: InferenceState = InferenceState.QUEUED) = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = FrameSource.MODEL,
        inferenceState = state,
    )

    private fun ready() = pending().copy(
        predictions = listOf(Prediction("Ascaris lumbricoides", 0.9f, 100f, 100f, 40f, 40f)),
        inferenceModelVersion = "yolo12n-effv2s-v1-tflite-fp16",
        inferenceState = InferenceState.READY,
    )

    // ── locked while pending ─────────────────────────────────────────────────────────────────

    @Test
    fun `a pending frame shows in progress and cannot be submitted`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(pending())
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.isAwaitingInference)
            assertEquals(ModelOutput.InProgress, state.frame!!.modelOutput())
            assertFalse(state.canSubmit)
            assertFalse("not a clean field", state.isCleanField)
        }

    @Test
    fun `annotation is locked while the frame is pending`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(pending())

            vm.onAddSpecies()
            vm.onUserNoteChanged("typed early")
            advanceUntilIdle()

            assertTrue(vm.state.value.findings.isEmpty())
            assertEquals("", vm.state.value.userNote)
            assertFalse(vm.state.value.hasUnsavedChanges)
        }

    // ── cancel ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cancel then dismiss leaves the sample pending`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(pending())

            vm.onCancelInferenceRequested()
            assertTrue(vm.state.value.showCancelInferenceConfirm)
            vm.onCancelInferenceDismissed()
            advanceUntilIdle()

            assertFalse(vm.state.value.showCancelInferenceConfirm)
            assertTrue(vm.state.value.isAwaitingInference)
            verify(cancelInference, never()).invoke(any())
        }

    @Test
    fun `cancel then confirm switches to manual verification, ready to annotate`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(cancelInference.invoke("sample-1")).thenReturn(Result.success(true))
            val vm = viewModel()
            vm.setFrame(pending(InferenceState.IN_INFERENCE))

            vm.onCancelInferenceRequested()
            vm.onCancelInferenceConfirmed()
            advanceUntilIdle()

            val state = vm.state.value
            verify(cancelInference).invoke("sample-1")
            assertFalse(state.showCancelInferenceConfirm)
            assertFalse(state.isAwaitingInference)
            assertTrue(state.isManual)
            assertEquals(ModelOutput.Unavailable, state.frame!!.modelOutput())
            assertEquals("manual", state.frame!!.inferenceModelVersion)
            // Annotation is open at once, and a manual field with nothing seen submits as is.
            assertTrue(state.canSubmit)
            vm.onAddSpecies()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.findings.size)
        }

    @Test
    fun `a result arriving after a confirmed cancel never appears`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(cancelInference.invoke("sample-1")).thenReturn(Result.success(true))
            val vm = viewModel()
            frames.value = listOf(pending())
            vm.setFrame(pending())
            vm.onCancelInferenceRequested()
            vm.onCancelInferenceConfirmed()
            advanceUntilIdle()

            // The store refuses the write (14zcqntj6ny), but even a stray re-emission carrying a
            // result must not reach a sample the medtech took over.
            frames.value = listOf(ready())
            advanceUntilIdle()

            assertTrue(vm.state.value.isManual)
            assertTrue(vm.state.value.frame!!.predictions.isEmpty())
        }

    @Test
    fun `when the result beat the cancel, the result is what shows`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(cancelInference.invoke("sample-1")).thenReturn(Result.success(false))
            val vm = viewModel()
            frames.value = listOf(pending())
            vm.setFrame(pending())

            vm.onCancelInferenceRequested()
            vm.onCancelInferenceConfirmed()
            frames.value = listOf(ready())
            advanceUntilIdle()

            assertEquals(InferenceState.READY, vm.state.value.frame!!.inferenceState)
            assertFalse(vm.state.value.isManual)
        }

    // ── the result lands while the screen is open ────────────────────────────────────────────

    @Test
    fun `a result landing while the screen is open replaces the indicator in place`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            frames.value = listOf(pending())
            vm.setFrame(pending())
            advanceUntilIdle()

            frames.value = listOf(ready())
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isAwaitingInference)
            assertEquals(
                ModelOutput.Read(listOf(ModelSpeciesCount("Ascaris lumbricoides", 1)), total = 1),
                state.frame!!.modelOutput(),
            )
            // Seeded from the model's box, exactly as a frame opened ready would be.
            assertEquals(1, state.findings.size)
            assertEquals(EggSpecies.ASCARIS, state.findings[0].answers.species)
            assertFalse(state.hasUnsavedChanges)
        }

    @Test
    fun `an open dialog closes when the result lands, since there is nothing left to cancel`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            frames.value = listOf(pending())
            vm.setFrame(pending())
            vm.onCancelInferenceRequested()

            frames.value = listOf(ready())
            advanceUntilIdle()

            assertFalse(vm.state.value.showCancelInferenceConfirm)
        }

    @Test
    fun `a ready frame's answers are never overwritten by a re-emission`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            frames.value = listOf(ready())
            vm.setFrame(ready())
            vm.onQ1Selected(false)
            advanceUntilIdle()

            frames.value = listOf(ready().copy(inferenceModelVersion = "re-emitted"))
            advanceUntilIdle()

            assertEquals(false, vm.state.value.findings[0].answers.isEgg)
        }

    // ── the unsaved-changes guard (#78) around the cancel flow ───────────────────────────────

    @Test
    fun `leaving a pending sample never asks, and leaving an edited cancelled one does`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(cancelInference.invoke("sample-1")).thenReturn(Result.success(true))
            val vm = viewModel()
            vm.setFrame(pending())

            vm.onCancel()
            advanceUntilIdle()
            assertNull("nothing to lose while pending", vm.state.value.pendingLeave)

            vm.setFrame(pending())
            vm.onCancelInferenceRequested()
            vm.onCancelInferenceConfirmed()
            advanceUntilIdle()
            assertFalse("cancelling is not an edit", vm.state.value.hasUnsavedChanges)

            vm.onUserNoteChanged("two Ascaris by hand")
            vm.onCancel()
            advanceUntilIdle()
            assertEquals(LeaveIntent.EXIT, vm.state.value.pendingLeave)
        }
}
