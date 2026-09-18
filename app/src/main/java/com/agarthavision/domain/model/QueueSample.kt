package com.agarthavision.domain.model

import java.time.Instant

/**
 * One row of the verification queue: a sample in the open session that is **still unverified**.
 *
 * The queue used to show the union of verified and unverified samples in two buckets, so that a
 * verified sample stayed visible and stayed editable in place (86d4ab4vm). Under the
 * patient-records plan that editing moves to the Sample Data Screen, reached from the Records
 * screen's Samples tab, and the queue goes back to meaning one thing: work still to do. This is a
 * relocation of that capability, not a removal of it.
 *
 * **There is no `isVerified` here, deliberately.** Every row in this list is unverified by
 * construction — the query that produces it selects `status = 'flagged'` — so the field would be
 * false on every row while still reading like a discriminator, and a count derived from it would
 * be a filter that matches nothing. This project has been bitten twice by exactly that shape
 * (`sessions.ended_at`, `detections.verified_by_user`). "Verified" keeps one definition, in
 * `ObserveVerificationQueueUseCase`, where it is now the predicate rather than a column carried
 * along for a reader to re-derive.
 *
 * Distinct from [FlaggedFrame], which is the in-flight capture payload: that type carries raw
 * JPEG bytes and has to hand-write `equals` to exclude them. **This one holds no `ByteArray`**,
 * which is exactly why it can use the generated `equals`/`hashCode` — there is nothing in it that
 * compares by reference. It carries [imagePath] instead and lets the image loader read and cache
 * the file. The row it replaced rendered from bytes re-read off disk on every emission:
 * survivable while the queue drained, an OOM risk once it grows. Keep it that way.
 *
 * The session a row belongs to is not on the row: it is the same session for every row, and the
 * all-done empty state needs it exactly when the list is empty and has none to read it from. It
 * travels on `VerificationQueue` instead.
 *
 * @property sampleId primary key, and the list key. **Never key rows by [capturedAt]** — two
 *   frames captured in the same millisecond once threw a duplicate-key exception.
 * @property capturedAt when the shutter was tapped. This doubles as the sample's label on the
 *   Verification Screen, so it is what the row leads with.
 * @property source whether the inference container answered or was unreachable. A
 *   [FrameSource.MANUAL] row has no model output at all, which is the one thing about an
 *   unverified sample the medtech cannot see from the thumbnail.
 */
data class QueueSample(
    val sampleId: String,
    val capturedAt: Instant,
    val imagePath: String,
    val source: FrameSource,
)
