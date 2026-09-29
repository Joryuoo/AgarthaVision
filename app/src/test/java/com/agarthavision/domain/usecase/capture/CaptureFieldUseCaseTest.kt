package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.InferenceQueue
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Capture saves the frame and returns; the model output comes later, from the inference queue
 * (14zcqntj6ny). None of these tests has an inference engine to mock, and that is the point:
 * the use case no longer holds one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureFieldUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flaggedFrameStore: FlaggedFrameStore = mock()
    private val inferenceQueue: InferenceQueue = mock()

    private val useCase = CaptureFieldUseCase(
        flaggedFrameStore = flaggedFrameStore,
        inferenceQueue = inferenceQueue,
    )

    @Test
    fun `capture saves a queued frame with no model output yet`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(flaggedFrameStore.add(any())).thenReturn("sample-1")

            val result = useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            assertEquals(Result.success(CaptureOutcome("sample-1")), result)

            val frameCaptor = argumentCaptor<FlaggedFrame>()
            verify(flaggedFrameStore).add(frameCaptor.capture())
            val frame = frameCaptor.firstValue
            assertEquals(InferenceState.QUEUED, frame.inferenceState)
            assertEquals(FrameSource.MODEL, frame.source)
            assertTrue(frame.predictions.isEmpty())
            assertNull(frame.inferenceModelVersion)
            assertEquals("session-1", frame.sessionId)
        }

    @Test
    fun `the queue is woken only after the frame is written`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Woken first, the queue could read the table, find nothing and go back to sleep
            // with this frame unseen until the next capture.
            whenever(flaggedFrameStore.add(any())).thenReturn("sample-1")

            useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            inOrder(flaggedFrameStore, inferenceQueue) {
                verify(flaggedFrameStore).add(any())
                verify(inferenceQueue).notifyQueued()
            }
        }

    @Test
    fun `a failed write fails the capture and does not wake the queue`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(flaggedFrameStore.add(any())).thenAnswer { error("disk full") }

            val result = useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            assertTrue(result.isFailure)
            verify(inferenceQueue, never()).notifyQueued()
        }
}
