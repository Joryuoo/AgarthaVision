package com.agarthavision.ui.verify

import app.cash.turbine.test
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
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
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Leaving a sample with edits on it asks first.
 *
 * Nothing on the Verification Screen is kept until Submit: paging to another sample re-seeds it
 * from scratch, and backing out drops everything. The cycle buttons sit beside the frame, where a
 * stray thumb lands, so every way off a sample that would lose work is held for a yes — and every
 * way off one with nothing to lose still goes straight through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnsavedChangesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frames = listOf("a", "b", "c").map(::frame)
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(MutableStateFlow(frames))
    }

    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase = mock {
        onBlocking { invoke(any()) } doReturn Result.success(emptyList())
    }

    private fun viewModel() = VerificationViewModel(
        flaggedFrameStore,
        mock<SubmitVerificationUseCase>(),
        searchSpeciesSuggestions,
    )

    private fun frame(id: String) = FlaggedFrame(
        sampleId = id,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    /** Opened on the middle sample, so both cycle buttons have somewhere to go. */
    private fun openOnMiddle() = viewModel().also { it.setFrame(frames[1]) }

    @Test
    fun `a sample only looked at pages without asking`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals("c", vm.state.value.frame?.sampleId)
            assertNull(vm.state.value.pendingLeave)
        }

    @Test
    fun `an edited sample holds the next button for a yes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onQ2Selected(false)

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals("b", vm.state.value.frame?.sampleId)
            assertEquals(LeaveIntent.NEXT_SAMPLE, vm.state.value.pendingLeave)
        }

    @Test
    fun `confirming goes where the medtech asked and drops the edits`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onUserNoteChanged("clumped")
            vm.onFramePrev()

            vm.onConfirmLeave()
            advanceUntilIdle()

            assertEquals("a", vm.state.value.frame?.sampleId)
            assertNull(vm.state.value.pendingLeave)
            assertEquals("", vm.state.value.userNote)
            assertFalse(vm.state.value.hasUnsavedChanges)
        }

    @Test
    fun `staying keeps the sample and every edit on it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onQ2Selected(false)
            vm.onFrameNext()

            vm.onDismissLeave()
            advanceUntilIdle()

            assertEquals("b", vm.state.value.frame?.sampleId)
            assertNull(vm.state.value.pendingLeave)
            assertEquals(false, vm.state.value.findings[0].answers.isBoxCorrect)
        }

    @Test
    fun `back on an edited sample does not dismiss until confirmed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onQ1Selected(false)

            vm.events.test {
                vm.onCancel()
                advanceUntilIdle()
                expectNoEvents()
                assertEquals(LeaveIntent.EXIT, vm.state.value.pendingLeave)

                vm.onConfirmLeave()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
        }

    @Test
    fun `an edit changed back is nothing to lose`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onQ2Selected(false)
            vm.onQ2Selected(true)

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals("c", vm.state.value.frame?.sampleId)
        }

    @Test
    fun `a cycle button with nowhere to go asks nothing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frames.last())
            vm.onQ2Selected(false)

            vm.onFrameNext()
            advanceUntilIdle()

            assertNull(vm.state.value.pendingLeave)
        }

    @Test
    fun `hiding the boxes or paging detections is not an edit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = openOnMiddle()
            vm.onToggleBoundingBoxes()
            vm.onDetectionNext()

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals("c", vm.state.value.frame?.sampleId)
        }
}
