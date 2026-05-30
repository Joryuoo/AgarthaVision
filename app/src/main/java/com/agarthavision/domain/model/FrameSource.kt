package com.agarthavision.domain.model

/**
 * Where a flagged frame entered the verification queue.
 *
 * - [MODEL] — the AI inference pipeline returned a positive prediction for this
 *   frame, so the FrameSampler queued it for medtech review.
 * - [MANUAL] — the medtech tapped the manual-capture button on Capture and
 *   tagged the frame with a species without AI involvement. Persists with
 *   `samples.is_manual = true` and nullable bbox columns (per ADR-005).
 */
enum class FrameSource {
    MODEL,
    MANUAL,
}
