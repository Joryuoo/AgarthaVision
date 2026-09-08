package com.agarthavision.domain.inference

/**
 * Where the wall-clock time went for a single inference call.
 *
 * Split finely enough that the benchmark harness can compare like with like: the remote engine
 * reports [networkMs] separately from [inferMs] so cloud *compute* can be compared against
 * device *compute* rather than against the medtech's wifi.
 */
data class InferenceTimings(
    /** Decode, letterbox, and normalise the frame. Zero for the remote engine. */
    val preprocessMs: Long = 0,
    /** Model execution only. For the remote engine, the server's own reported compute time. */
    val inferMs: Long = 0,
    /** Decode raw output, apply NMS, rescale boxes. Zero for the remote engine. */
    val postprocessMs: Long = 0,
    /** Request/response overhead. Zero for the on-device engine. */
    val networkMs: Long = 0,
    /** End-to-end, as observed by the caller. Always the number to quote for throughput. */
    val totalMs: Long = 0,
)
