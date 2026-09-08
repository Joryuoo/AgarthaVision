package com.agarthavision.domain.inference

/**
 * A backend that turns a JPEG frame into detections.
 *
 * Two implementations exist — the cloud container and the on-device model — and the capture
 * loop is indifferent to which one it is holding. Implementations live in `data/inference/`
 * per constraint C3; this interface is the only thing `domain/` knows about.
 */
interface InferenceEngine {

    /** Identifies this backend in results, logs, and benchmark output. */
    val id: InferenceEngineId

    /**
     * Whether this engine can serve a request right now.
     *
     * The on-device engine reports `false` when no model has been installed; the remote engine
     * reports `false` when the container is unreachable. The selector uses this to route.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Runs inference over [jpegBytes].
     *
     * Returns every box the model produced — no clinical filtering happens here, because the
     * human is the threshold (constraint C7).
     *
     * @throws com.agarthavision.domain.usecase.inference.InferenceConnectionException
     *   when the backend cannot be reached or fails.
     */
    suspend fun infer(jpegBytes: ByteArray): InferenceResult
}
