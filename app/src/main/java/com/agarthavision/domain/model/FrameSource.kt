package com.agarthavision.domain.model

/**
 * Where a flagged frame entered the verification queue.
 *
 * - [MODEL] — the frame goes to a model. Its output may still be on its way: read
 *   [com.agarthavision.domain.inference.InferenceState] before treating an empty prediction
 *   list as a clean field.
 * - [MANUAL] — no model output, ever: captured with no model involved, inference cancelled by
 *   the medtech, or both engines failing until the queue gave up. Persists with
 *   `samples.is_manual = true` and nullable bbox columns (per ADR-005).
 */
enum class FrameSource {
    MODEL,
    MANUAL,
}
