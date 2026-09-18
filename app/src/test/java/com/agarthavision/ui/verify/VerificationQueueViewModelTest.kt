package com.agarthavision.ui.verify

import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.QueueSample
import com.agarthavision.domain.usecase.verify.DeleteQueueItemsUseCase
import com.agarthavision.domain.usecase.verify.ObserveVerificationQueueUseCase
import com.agarthavision.domain.usecase.verify.OpenVerificationTargetUseCase
import com.agarthavision.domain.usecase.verify.VerificationQueue
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
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * The flat queue's contract.
 *
 * Every row is unverified, so the two things worth pinning are what happens when a row *stops*
 * being unverified — it leaves, and takes any selection of it with it — and that an empty list
 * still knows which of its two meanings it has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationQueueViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val queue = MutableStateFlow(VerificationQueue())

    private val observeVerificationQueue: ObserveVerificationQueueUseCase =
        mock<ObserveVerificationQueueUseCase>().also { whenever(it.invoke()).thenReturn(queue) }
    private val openVerificationTarget: OpenVerificationTargetUseCase = mock()
    private val deleteQueueItems: DeleteQueueItemsUseCase = mock()

    private fun viewModel() = VerificationQueueViewModel(
        observeVerificationQueue,
        openVerificationTarget,
        deleteQueueItems,
    )

    private fun sample(id: String, source: FrameSource = FrameSource.MODEL) = QueueSample(
        sampleId = id,
        capturedAt = Instant.EPOCH,
        imagePath = "/tmp/$id.jpg",
        source = source,
    )

    @Test
    fun `the queue is one list, whatever a row's source`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            // A frame the model read and a frame captured with the container down sit together:
            // source files nothing, it only changes what the verification screen has to offer.
            queue.value = VerificationQueue(
                samples = listOf(sample("a"), sample("b", FrameSource.MANUAL)),
                verifiedInSession = 0,
                sessionId = "session-1",
            )
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), vm.state.value.samples.map { it.sampleId })
            assertEquals("session-1", vm.state.value.sessionId)
        }

    /**
     * Verifying a sample is now the ordinary way a row leaves the queue, so a selection that
     * outlives it would leave a phantom in the contextual bar's count — and a delete confirmed
     * against rows the medtech can no longer see is exactly the mistake an irreversible action
     * must not allow.
     */
    @Test
    fun `a sample that gets verified leaves the queue and leaves the selection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            queue.value = VerificationQueue(
                samples = listOf(sample("a"), sample("b")),
                sessionId = "session-1",
            )
            advanceUntilIdle()

            vm.onToggleSelected(sample("a"))
            vm.onToggleSelected(sample("b"))
            assertEquals(setOf("a", "b"), vm.state.value.selectedIds)

            // "a" was verified: it is out of the flagged set and into the verified count.
            queue.value = VerificationQueue(
                samples = listOf(sample("b")),
                verifiedInSession = 1,
                sessionId = "session-1",
            )
            advanceUntilIdle()

            assertEquals(setOf("b"), vm.state.value.selectedIds)
            assertTrue(vm.state.value.isSelecting)
        }

    /**
     * The empty state's two meanings, carried by the count rather than by the list.
     *
     * An empty queue is silent about whether anything was ever captured, which is why the count
     * travels with it.
     */
    @Test
    fun `an emptied queue keeps the count that tells the medtech they finished`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            queue.value = VerificationQueue(samples = listOf(sample("a")), sessionId = "session-1")
            advanceUntilIdle()
            assertEquals(
                QueueEmptyVariant.NEVER_HAD,
                queueEmptyVariant(vm.state.value.verifiedInSession),
            )

            queue.value = VerificationQueue(
                samples = emptyList(),
                verifiedInSession = 1,
                sessionId = "session-1",
            )
            advanceUntilIdle()

            assertTrue(vm.state.value.samples.isEmpty())
            assertEquals(
                QueueEmptyVariant.ALL_DONE,
                queueEmptyVariant(vm.state.value.verifiedInSession),
            )
            // Still reachable: the route onward needs a session id the empty list cannot supply.
            assertEquals("session-1", vm.state.value.sessionId)
        }

    @Test
    fun `clearing the selection leaves the rows alone`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            queue.value = VerificationQueue(samples = listOf(sample("a")), sessionId = "session-1")
            advanceUntilIdle()
            vm.onToggleSelected(sample("a"))

            vm.onClearSelection()

            assertFalse(vm.state.value.isSelecting)
            assertEquals(1, vm.state.value.samples.size)
        }

    @Test
    fun `a delete cannot be confirmed with nothing selected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onDeleteRequested()

            assertFalse(vm.state.value.showDeleteConfirm)
        }
}
