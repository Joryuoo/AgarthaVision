package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.InferenceEngine
import com.agarthavision.domain.inference.InferenceEngineId
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureFieldUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inferenceEngine: InferenceEngine = mock()
    private val flaggedFrameStore: FlaggedFrameStore = mock()

    private val useCase = CaptureFieldUseCase(
        inferenceEngine = inferenceEngine,
        flaggedFrameStore = flaggedFrameStore,
    )

    private val prediction = Prediction(
        classLabel = "Ascaris",
        confidence = 0.91f,
        x = 10f,
        y = 20f,
        width = 30f,
        height = 40f,
    )

    @Test
    fun `infer with predictions persists a MODEL frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val inferenceResult = InferenceResult(
                predictions = listOf(prediction),
                imageWidth = 1024,
                imageHeight = 768,
                modelVersion = "v2",
                engine = InferenceEngineId.REMOTE,
            )
            whenever(inferenceEngine.infer(any())).thenReturn(inferenceResult)

            val result = useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            assertEquals(Result.success(FrameSource.MODEL), result)

            val frameCaptor = argumentCaptor<FlaggedFrame>()
            verify(flaggedFrameStore).add(frameCaptor.capture())

            val frame = frameCaptor.firstValue
            assertEquals(FrameSource.MODEL, frame.source)
            assertEquals(listOf(prediction), frame.predictions)
            assertEquals(1, frame.predictions.size)
        }

    @Test
    fun `infer with zero detections still persists a MODEL frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val inferenceResult = InferenceResult(
                predictions = emptyList(),
                imageWidth = 1024,
                imageHeight = 768,
                modelVersion = "v2",
                engine = InferenceEngineId.REMOTE,
            )
            whenever(inferenceEngine.infer(any())).thenReturn(inferenceResult)

            val result = useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            assertEquals(Result.success(FrameSource.MODEL), result)

            val frameCaptor = argumentCaptor<FlaggedFrame>()
            verify(flaggedFrameStore).add(frameCaptor.capture())

            val frame = frameCaptor.firstValue
            assertEquals(FrameSource.MODEL, frame.source)
            assertTrue(frame.predictions.isEmpty())
        }

    @Test
    fun `unreachable container falls back to a MANUAL frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(inferenceEngine.infer(any())).thenAnswer { throw InferenceConnectionException() }

            val result = useCase(sessionId = "session-1", jpegBytes = ByteArray(5))

            assertEquals(Result.success(FrameSource.MANUAL), result)

            val frameCaptor = argumentCaptor<FlaggedFrame>()
            verify(flaggedFrameStore).add(frameCaptor.capture())

            val frame = frameCaptor.firstValue
            assertEquals(FrameSource.MANUAL, frame.source)
            assertTrue(frame.predictions.isEmpty())
            assertNull(frame.inferenceModelVersion)
        }
}
