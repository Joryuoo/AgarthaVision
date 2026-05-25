package com.agarthavision.domain.model

import java.util.UUID

/**
 * Domain model for a captured microscopy sample.
 *
 * In Phase 1, a Sample exists in Room only after the user verifies a flagged frame
 * (status `VERIFIED`), and is upgraded to `SYNCED` after a successful Supabase upload.
 *
 * Additional fields (`userId`, `verifiedAt`, `storagePath`, `inferenceModelVersion`)
 * may be added in Sprint 1 implementation tracks as the sync flow is built out.
 * See docs/03_MOBILE_APP_PLAN.md §1.10.
 */
data class Sample(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val verifiedAt: Long = timestamp,
    val deviceId: String,
    val sessionId: String,
    val filePath: String,
    val storagePath: String? = null,
    val inferenceModelVersion: String = "unknown",
    val needsReannotation: Boolean = false,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float? = null,
    val status: SampleStatus = SampleStatus.FLAGGED,
)
