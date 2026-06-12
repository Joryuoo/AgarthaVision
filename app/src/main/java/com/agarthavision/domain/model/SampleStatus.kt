package com.agarthavision.domain.model

/**
 * Phase 1 sample lifecycle.
 *
 * `FLAGGED` is the in-memory + disk-cache state for a frame that Roboflow/Inference
 * flagged but the user has not yet verified. `VERIFIED` is the state after the user
 * taps Verify in the sheet. `SYNCED` is the state after a successful upload to Supabase.
 * `SYNC_FAILED` is set when the upload fails and a retry is queued.
 *
 * See CONTEXT.md and TODO.md
 */
enum class SampleStatus(val value: String) {
    FLAGGED("flagged"),
    VERIFIED("verified"),
    SYNCED("synced"),
    SYNC_FAILED("sync_failed"),
}
