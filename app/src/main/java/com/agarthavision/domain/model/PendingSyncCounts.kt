package com.agarthavision.domain.model

/**
 * Rows awaiting upload for the signed-in medtech, split by type, plus a separate
 * count of rows that have already failed a sync attempt. Backs the Settings
 * Data & Sync section. Per ADR-007.
 */
data class PendingSyncCounts(
    val pendingSessions: Int,
    val pendingSamples: Int,
    val pendingReports: Int,
    val failed: Int,
) {
    /** Total rows still awaiting upload, across all three types. */
    val totalPending: Int
        get() = pendingSessions + pendingSamples + pendingReports

    /** True when nothing is pending and nothing has failed. */
    val allSynced: Boolean
        get() = totalPending == 0 && failed == 0
}
