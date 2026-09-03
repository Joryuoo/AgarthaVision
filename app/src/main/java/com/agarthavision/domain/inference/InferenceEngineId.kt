package com.agarthavision.domain.inference

/**
 * Which backend produced a set of predictions.
 *
 * Recorded on every [InferenceResult] so a flagged frame can always be traced back to the
 * engine that flagged it — the cloud container or the phone itself. See
 * `docs/map/processes/infer.md`.
 */
enum class InferenceEngineId(val value: String) {
    /** The self-hosted FastAPI container reached over HTTP. */
    REMOTE("remote"),

    /** The exported model running on this device's CPU/GPU/NPU. */
    ON_DEVICE("on_device"),
    ;

    companion object {
        fun fromValue(value: String): InferenceEngineId? = entries.firstOrNull { it.value == value }
    }
}
