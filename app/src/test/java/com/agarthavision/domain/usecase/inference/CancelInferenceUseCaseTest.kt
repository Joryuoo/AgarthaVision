package com.agarthavision.domain.usecase.inference

import com.agarthavision.domain.inference.FakeInferenceQueueRepository
import com.agarthavision.domain.inference.InferenceEngineId
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelInferenceUseCaseTest {

    private val repository = FakeInferenceQueueRepository()
    private val cancel = CancelInferenceUseCase(repository)

    @Test
    fun `cancelling a queued sample makes it manual`() = runTest {
        repository.add("a", capturedAt = 100)

        assertEquals(Result.success(true), cancel("a"))
        assertEquals(InferenceState.MANUAL, repository.stateOf("a"))
    }

    @Test
    fun `cancelling a sample whose result already landed reports nothing to cancel`() = runTest {
        repository.add("a", capturedAt = 100)
        repository.claim("a")
        repository.complete("a", readyResult())

        assertEquals(Result.success(false), cancel("a"))
        assertEquals(InferenceState.READY, repository.stateOf("a"))
    }

    @Test
    fun `a blank id is a failure, not a silent no-op`() = runTest {
        assertTrue(cancel(" ").isFailure)
    }

    private fun readyResult() = InferenceResult(
        predictions = emptyList(),
        imageWidth = 640,
        imageHeight = 480,
        modelVersion = "yolo26n-effv2b0-v1-cloud-fp32",
        engine = InferenceEngineId.REMOTE,
    )
}
