package com.agarthavision.domain.inference

import com.agarthavision.domain.repository.InferenceQueueRepository
import com.agarthavision.domain.usecase.inference.InferenceConnectionException

/**
 * An in-memory [InferenceQueueRepository] with the same conditional-write rules as
 * `SampleDao`'s queue queries: every transition names the state it expects, and changes
 * nothing when the row has moved on. `SampleDaoInferenceQueueTest` pins the real SQL to the
 * same rules, so a test that passes here is testing the processor, not the fake.
 */
internal class FakeInferenceQueueRepository : InferenceQueueRepository {

    data class Row(
        val sampleId: String,
        val capturedAt: Long,
        val jpeg: ByteArray?,
        val state: InferenceState = InferenceState.QUEUED,
        val attempts: Int = 0,
        val result: InferenceResult? = null,
    )

    val rows = linkedMapOf<String, Row>()

    /** How many times each sample was written as complete. More than one would be a bug. */
    val completions = mutableMapOf<String, Int>()

    fun add(sampleId: String, capturedAt: Long, jpeg: ByteArray? = sampleId.toByteArray()) {
        rows[sampleId] = Row(sampleId, capturedAt, jpeg)
    }

    fun delete(sampleId: String) {
        rows.remove(sampleId)
    }

    fun stateOf(sampleId: String): InferenceState? = rows[sampleId]?.state

    override suspend fun requeueInterrupted() {
        rows.replaceAll { _, row ->
            if (row.state == InferenceState.IN_INFERENCE) row.copy(state = InferenceState.QUEUED) else row
        }
    }

    override suspend fun queuedSampleIds(): List<String> = rows.values
        .filter { it.state == InferenceState.QUEUED }
        .sortedWith(compareBy<Row> { it.capturedAt }.thenBy { it.sampleId })
        .map { it.sampleId }

    override suspend fun claim(sampleId: String): Boolean =
        transition(sampleId, InferenceState.QUEUED) { it.copy(state = InferenceState.IN_INFERENCE) }

    override suspend fun readFrame(sampleId: String): ByteArray? = rows[sampleId]?.jpeg

    override suspend fun complete(sampleId: String, result: InferenceResult): Boolean {
        val written = transition(sampleId, InferenceState.IN_INFERENCE) {
            it.copy(state = InferenceState.READY, result = result)
        }
        if (written) completions.merge(sampleId, 1, Int::plus)
        return written
    }

    override suspend fun recordFailure(sampleId: String, maxAttempts: Int): InferenceState? {
        val written = transition(sampleId, InferenceState.IN_INFERENCE) {
            val attempts = it.attempts + 1
            it.copy(
                attempts = attempts,
                state = if (attempts >= maxAttempts) InferenceState.MANUAL else InferenceState.QUEUED,
            )
        }
        return if (written) rows.getValue(sampleId).state else null
    }

    override suspend fun cancel(sampleId: String): Boolean {
        val row = rows[sampleId]?.takeIf { it.state.isPending } ?: return false
        rows[sampleId] = row.copy(state = InferenceState.MANUAL)
        return true
    }

    private fun transition(sampleId: String, expected: InferenceState, change: (Row) -> Row): Boolean {
        val row = rows[sampleId]?.takeIf { it.state == expected } ?: return false
        rows[sampleId] = change(row)
        return true
    }
}

/**
 * An engine whose answer per frame is scripted. Frames are identified by their bytes, which the
 * fake repository sets to the sample id.
 */
internal class ScriptedEngine(
    override val id: InferenceEngineId,
    private val modelVersion: String,
) : InferenceEngine {

    /** Sample ids this engine was asked about, in order. */
    val calls = mutableListOf<String>()

    /** Frames this engine fails on. Everything fails when [failEverything] is set. */
    val failingFrames = mutableSetOf<String>()
    var failEverything = false

    /** Runs while the frame is "in flight", before the answer comes back. */
    var onInfer: suspend (sampleId: String) -> Unit = {}

    override suspend fun isAvailable(): Boolean = !failEverything

    override suspend fun infer(jpegBytes: ByteArray): InferenceResult {
        val sampleId = String(jpegBytes)
        calls += sampleId
        onInfer(sampleId)
        if (failEverything || sampleId in failingFrames) {
            throw InferenceConnectionException(IllegalStateException("$id failed on $sampleId"))
        }
        return InferenceResult(
            predictions = listOf(
                Prediction(
                    classLabel = "Ascaris lumbricoides",
                    confidence = 0.9f,
                    x = 1f,
                    y = 2f,
                    width = 3f,
                    height = 4f,
                ),
            ),
            imageWidth = 640,
            imageHeight = 480,
            modelVersion = modelVersion,
            engine = id,
        )
    }
}
