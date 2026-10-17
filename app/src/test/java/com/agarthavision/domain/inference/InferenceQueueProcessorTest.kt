package com.agarthavision.domain.inference

import com.agarthavision.domain.inference.InferenceQueueProcessor.DrainSummary
import com.agarthavision.domain.repository.InferenceQueueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/**
 * The inference queue's consumer (14zcqntj6ny): ordering, the cloud-to-device fallback, the
 * circuit breaker, the retry limit, cancel-versus-result, and recovery after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InferenceQueueProcessorTest {

    private val repository = FakeInferenceQueueRepository()
    private val cloud = ScriptedEngine(InferenceEngineId.REMOTE, CLOUD_VERSION)
    private val device = ScriptedEngine(InferenceEngineId.ON_DEVICE, DEVICE_VERSION)
    private val time = TestTimeSource()
    private val breaker = CloudCircuitBreaker(timeSource = time)

    private fun processor(timeSource: TimeSource.WithComparableMarks = time) =
        InferenceQueueProcessor(
            repository = repository,
            cloudEngine = cloud,
            deviceEngine = device,
            circuitBreaker = breaker,
            timeSource = timeSource,
            maxAttempts = 3,
            firstRetryDelay = 30.seconds,
            maxRetryDelay = 5.minutes,
        )

    // ── ordering ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `frames are processed oldest capture first, one at a time`() = runTest {
        repository.add("c", capturedAt = 300)
        repository.add("a", capturedAt = 100)
        repository.add("b", capturedAt = 200)
        var inFlight = 0
        cloud.onInfer = {
            inFlight++
            assertEquals("one frame at a time", 1, inFlight)
            inFlight--
        }

        processor().drain()

        assertEquals(listOf("a", "b", "c"), cloud.calls)
    }

    @Test
    fun `a frame captured mid-pass is picked up in the same pass`() = runTest {
        repository.add("a", capturedAt = 100)
        cloud.onInfer = { sampleId -> if (sampleId == "a") repository.add("late", capturedAt = 500) }

        val summary = processor().drain()

        assertEquals(listOf("a", "late"), cloud.calls)
        assertEquals(2, summary.completed)
    }

    // ── cloud first, device fallback ─────────────────────────────────────────────────────────

    @Test
    fun `with the cloud up, frames get the cloud's result`() = runTest {
        repository.add("a", capturedAt = 100)

        processor().drain()

        assertEquals(InferenceState.READY, repository.stateOf("a"))
        assertEquals(CLOUD_VERSION, repository.rows.getValue("a").result?.modelVersion)
        assertTrue("the device is never asked", device.calls.isEmpty())
    }

    @Test
    fun `with the cloud down, the same frame goes to the device`() = runTest {
        repository.add("a", capturedAt = 100)
        cloud.failEverything = true

        processor().drain()

        assertEquals(listOf("a"), cloud.calls)
        assertEquals(listOf("a"), device.calls)
        assertEquals(InferenceState.READY, repository.stateOf("a"))
        // The result records which engine produced it (14zcqntj6nw).
        assertEquals(DEVICE_VERSION, repository.rows.getValue("a").result?.modelVersion)
    }

    // ── circuit breaker ──────────────────────────────────────────────────────────────────────

    @Test
    fun `after repeated cloud failures, frames skip the cloud until the cool-down ends`() = runTest {
        (1..6).forEach { repository.add("f$it", capturedAt = it.toLong()) }
        cloud.failEverything = true

        processor().drain()

        // Three frames pay for the cloud's failure; the other three go straight to the device.
        assertEquals(listOf("f1", "f2", "f3"), cloud.calls)
        assertEquals((1..6).map { "f$it" }, device.calls)
        assertTrue((1..6).all { repository.stateOf("f$it") == InferenceState.READY })
    }

    @Test
    fun `once the cool-down passes, the cloud is probed again and a success closes the breaker`() = runTest {
        (1..3).forEach { repository.add("f$it", capturedAt = it.toLong()) }
        cloud.failEverything = true
        val processor = processor()
        processor.drain()

        cloud.failEverything = false
        repository.add("g1", capturedAt = 10)
        repository.add("g2", capturedAt = 11)
        time += CloudCircuitBreaker.DEFAULT_BASE_COOLDOWN
        processor.drain()

        assertEquals(listOf("f1", "f2", "f3", "g1", "g2"), cloud.calls)
        assertEquals(CLOUD_VERSION, repository.rows.getValue("g2").result?.modelVersion)
        assertEquals(CloudCircuitBreaker.State.CLOSED, breaker.state)
    }

    // ── both engines fail ────────────────────────────────────────────────────────────────────

    @Test
    fun `when both engines fail the frame stays queued, and later frames still run`() = runTest {
        repository.add("broken", capturedAt = 100)
        repository.add("fine", capturedAt = 200)
        cloud.failingFrames += "broken"
        device.failingFrames += "broken"

        val summary = processor().drain()

        assertEquals(InferenceState.QUEUED, repository.stateOf("broken"))
        assertEquals(1, repository.rows.getValue("broken").attempts)
        assertEquals(InferenceState.READY, repository.stateOf("fine"))
        assertEquals(DrainSummary(completed = 1, retrying = 1), summary)
    }

    @Test
    fun `a failed frame waits out its retry delay before it is tried again`() = runTest {
        repository.add("broken", capturedAt = 100)
        cloud.failingFrames += "broken"
        device.failingFrames += "broken"
        val processor = processor()
        processor.drain()

        processor.drain()
        assertEquals("not due yet", 1, device.calls.size)

        time += 30.seconds
        processor.drain()
        assertEquals(2, device.calls.size)

        // Doubled: the next retry is a minute away, not thirty seconds.
        time += 30.seconds
        processor.drain()
        assertEquals(2, device.calls.size)
    }

    @Test
    fun `at the retry limit the frame becomes manual`() = runTest {
        repository.add("broken", capturedAt = 100)
        cloud.failEverything = true
        device.failEverything = true
        val processor = processor()

        repeat(3) {
            processor.drain()
            time += 5.minutes
        }

        assertEquals(InferenceState.MANUAL, repository.stateOf("broken"))
        assertEquals(3, repository.rows.getValue("broken").attempts)
        assertNull(repository.rows.getValue("broken").result)
        assertTrue(repository.queuedSampleIds().isEmpty())
    }

    @Test
    fun `a frame whose image is gone counts as a failure, not a crash`() = runTest {
        repository.add("no-image", capturedAt = 100, jpeg = null)

        val summary = processor().drain()

        assertEquals(DrainSummary(retrying = 1), summary)
        assertEquals(InferenceState.QUEUED, repository.stateOf("no-image"))
    }

    // ── cancel and delete versus a landing result ────────────────────────────────────────────

    @Test
    fun `a result arriving after a cancel is discarded`() = runTest {
        repository.add("a", capturedAt = 100)
        // The medtech cancels while the cloud is working on the frame.
        cloud.onInfer = { sampleId -> assertTrue(repository.cancel(sampleId)) }

        val summary = processor().drain()

        assertEquals(DrainSummary(discarded = 1), summary)
        assertEquals(InferenceState.MANUAL, repository.stateOf("a"))
        assertNull("the cancelled sample never gets a model output", repository.rows.getValue("a").result)
    }

    @Test
    fun `a cancel after the result landed changes nothing`() = runTest {
        repository.add("a", capturedAt = 100)
        processor().drain()

        assertTrue(!repository.cancel("a"))
        assertEquals(InferenceState.READY, repository.stateOf("a"))
    }

    @Test
    fun `a frame deleted while in inference is discarded`() = runTest {
        repository.add("a", capturedAt = 100)
        cloud.onInfer = { sampleId -> repository.delete(sampleId) }

        val summary = processor().drain()

        assertEquals(DrainSummary(discarded = 1), summary)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `a frame cancelled before its turn is skipped without asking any engine`() = runTest {
        repository.add("a", capturedAt = 100)
        repository.cancel("a")

        processor().drain()

        assertTrue(cloud.calls.isEmpty())
        assertTrue(device.calls.isEmpty())
    }

    // ── restart recovery ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a frame interrupted mid-inference is picked up again after a restart, once`() = runTest {
        repository.add("a", capturedAt = 100)
        repository.add("b", capturedAt = 200)
        // The process died with "a" claimed and its result never written.
        repository.claim("a")

        processor().drain()

        assertEquals(InferenceState.READY, repository.stateOf("a"))
        assertEquals(InferenceState.READY, repository.stateOf("b"))
        assertEquals(mapOf("a" to 1, "b" to 1), repository.completions)
        assertEquals(listOf("a", "b"), cloud.calls)
    }

    @Test
    fun `a finished frame is never processed again`() = runTest {
        repository.add("a", capturedAt = 100)
        val processor = processor()

        processor.drain()
        processor.drain()

        assertEquals(listOf("a"), cloud.calls)
        assertEquals(mapOf("a" to 1), repository.completions)
    }

    // ── passes and retries, as the worker drives them ──────────────────────────────────────────

    @Test
    fun `with nothing failed there is no retry to schedule`() = runTest {
        repository.add("a", capturedAt = 100)
        val processor = processor()
        processor.drain()

        assertNull(processor.nextRetryIn())
    }

    @Test
    fun `the next retry is when the earliest failed frame falls due`() = runTest {
        repository.add("broken", capturedAt = 100)
        cloud.failEverything = true
        device.failEverything = true
        val processor = processor()
        processor.drain()
        assertEquals(30.seconds, processor.nextRetryIn())

        time += 10.seconds
        assertEquals(20.seconds, processor.nextRetryIn())

        time += 20.seconds
        processor.drain()
        assertEquals("doubled after the second failure", 1.minutes, processor.nextRetryIn())
    }

    @Test
    fun `a frame that stops failing leaves no retry behind`() = runTest {
        repository.add("flaky", capturedAt = 100)
        cloud.failEverything = true
        device.failEverything = true
        val processor = processor()
        processor.drain()

        cloud.failEverything = false
        device.failEverything = false
        time += 30.seconds
        processor.drain()

        assertEquals(InferenceState.READY, repository.stateOf("flaky"))
        assertNull(processor.nextRetryIn())
    }

    @Test
    fun `a pass that cannot read the queue throws, and the next pass carries on`() = runTest {
        repository.add("a", capturedAt = 100)
        var failNextListing = true
        val flaky = object : InferenceQueueRepository by repository {
            override suspend fun queuedSampleIds(): List<String> {
                if (failNextListing) {
                    failNextListing = false
                    error("database is locked")
                }
                return repository.queuedSampleIds()
            }
        }
        val processor = InferenceQueueProcessor(
            repository = flaky,
            cloudEngine = cloud,
            deviceEngine = device,
            circuitBreaker = breaker,
            timeSource = time,
        )

        assertTrue(runCatching { processor.drain() }.isFailure)
        assertTrue(cloud.calls.isEmpty())

        processor.drain()
        assertEquals(listOf("a"), cloud.calls)
    }

    @Test
    fun `two passes started together never overlap`() = runTest {
        repository.add("a", capturedAt = 100)
        val release = CompletableDeferred<Unit>()
        cloud.onInfer = { release.await() }
        val processor = processor()

        launch { processor.drain() }
        runCurrent()
        launch { processor.drain() }
        runCurrent()

        // Without the lock the second pass would have put "a" back in the queue mid-flight and
        // sent it to the cloud a second time.
        assertEquals(InferenceState.IN_INFERENCE, repository.stateOf("a"))
        assertEquals(listOf("a"), cloud.calls)

        release.complete(Unit)
        runCurrent()
        assertEquals(mapOf("a" to 1), repository.completions)
        assertEquals(listOf("a"), cloud.calls)
    }

    private companion object {
        const val CLOUD_VERSION = "yolo26n-effv2b0-v1-cloud-fp32"
        const val DEVICE_VERSION = "yolo26n-effv2b0-v1-tflite-fp32"
    }
}
