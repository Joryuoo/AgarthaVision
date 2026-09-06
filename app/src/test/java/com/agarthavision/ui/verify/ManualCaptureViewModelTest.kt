package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.SubmitManualCaptureUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ManualCaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storeState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(storeState)
    }
    private val submitManualCaptureUseCase: SubmitManualCaptureUseCase = mock()

    private fun viewModel() =
        ManualCaptureViewModel(flaggedFrameStore, submitManualCaptureUseCase)

    private fun manualFrame(sampleId: String) = FlaggedFrame(
        sampleId = sampleId,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = FrameSource.MANUAL,
    )

    private fun aiFrame(sampleId: String) =
        manualFrame(sampleId).copy(source = FrameSource.MODEL)

    @Test
    fun `frameIndexInQueue advances with onFrameNext`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b", "c").map { manualFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[0])
            advanceUntilIdle()
            assertEquals(1, vm.state.value.frameIndexInQueue)
            assertEquals(3, vm.state.value.queueSize)

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals(frames[1], vm.state.value.frame)
            assertEquals(2, vm.state.value.frameIndexInQueue)
        }

    @Test
    fun `frameIndexInQueue retreats with onFramePrev`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b").map { manualFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[1])
            advanceUntilIdle()

            vm.onFramePrev()
            advanceUntilIdle()

            assertEquals(frames[0], vm.state.value.frame)
            assertEquals(1, vm.state.value.frameIndexInQueue)
        }

    @Test
    fun `canGoPrev is false on the first frame and canGoNext false on the last`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b").map { manualFrame(it) }
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
    fun `frame cycling skips AI frames`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Model detections belong to VerificationSheet; paging onto one here would
            // keep rendering the manual sheet against a frame with detections to review.
            val manual1 = manualFrame("m-1")
            val manual2 = manualFrame("m-2")
            storeState.value = listOf(manual1, aiFrame("ai-1"), manual2)
            val vm = viewModel()
            vm.setFrame(manual1)
            advanceUntilIdle()

            assertEquals(2, vm.state.value.queueSize)

            vm.onFrameNext()
            advanceUntilIdle()

            assertEquals(manual2, vm.state.value.frame)
            assertFalse(vm.state.value.canGoNext)
        }

    @Test
    fun `onDeleteFrame advances to the next manual frame instead of dismissing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val frames = listOf("a", "b").map { manualFrame(it) }
            storeState.value = frames
            val vm = viewModel()
            vm.setFrame(frames[0])
            advanceUntilIdle()

            vm.onDeleteFrame()
            advanceUntilIdle()

            verify(flaggedFrameStore).remove(frames[0])
            assertEquals(frames[1], vm.state.value.frame)
        }

    @Test
    fun `onDeleteFrame dismisses when it was the last manual frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val only = manualFrame("only")
            storeState.value = listOf(only)
            val vm = viewModel()
            vm.setFrame(only)
            advanceUntilIdle()

            var dismissed = false
            val job = kotlinx.coroutines.CoroutineScope(
                mainDispatcherRule.testDispatcher,
            ).launch {
                vm.events.collect { if (it is ManualCaptureEvent.Dismiss) dismissed = true }
            }
            advanceUntilIdle()

            vm.onDeleteFrame()
            advanceUntilIdle()

            assertTrue(dismissed)
            job.cancel()
        }
}
