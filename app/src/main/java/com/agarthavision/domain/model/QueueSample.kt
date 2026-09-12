package com.agarthavision.domain.model

import java.time.Instant

/**
 * The two things the verification queue files samples under.
 *
 * Source — AI or manual — is deliberately **not** a second dimension. It is shown on the row and
 * it decides what the verification screen renders, but it files nothing (revised 2026-09-12).
 * That also retires a subtlety the four-bucket version had to be careful about: a zero-detection
 * AI capture is a real negative result and had to stay under AI, which cannot go wrong when
 * source selects no bucket at all.
 */
enum class QueueBucket { UNVERIFIED, VERIFIED }

/**
 * One row of the verification queue.
 *
 * Distinct from [FlaggedFrame], which is the in-flight capture payload: that type carries raw
 * JPEG bytes and has to hand-write `equals` to exclude them. **This one holds no `ByteArray`**,
 * which is exactly why it can use the generated `equals`/`hashCode` — there is nothing in it
 * that compares by reference. It carries [imagePath] instead and lets the image loader read and
 * cache the file, so a queue that now holds every sample in the session does not re-read every
 * JPEG on every emission.
 *
 * [isVerified] is the mutable field here, and it **must** stay in `equals`. The queue is carried
 * in a `StateFlow`, which conflates an emission equal to the value it already holds; drop this
 * and the emission where a sample moves Unverified → Verified is swallowed and the bucket goes
 * stale. That is not hypothetical — the same bug shipped once on `FlaggedFrame`.
 */
data class QueueSample(
    val sampleId: String,
    val sessionId: String,
    val capturedAt: Instant,
    val imagePath: String,
    val source: FrameSource,
    val isVerified: Boolean,
    val verifiedAt: Instant?,
    val confirmedDetections: Int,
    val userNote: String?,
) {
    /**
     * Which bucket this row belongs in — a total function of one boolean, so a sample is in
     * exactly one bucket by construction and a count can never disagree with its list.
     */
    val bucket: QueueBucket
        get() = if (isVerified) QueueBucket.VERIFIED else QueueBucket.UNVERIFIED
}
