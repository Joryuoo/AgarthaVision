package com.agarthavision.domain.model

/**
 * Rows awaiting upload for the signed-in medtech, split by type, plus a separate
 * count of rows that have already failed a sync attempt. Backs the Settings
 * Data & Sync section. Per ADR-007.
 *
 * Patients are counted here and not only in the push summary. PB-08a exists so a medtech
 * who signs in at the clinic arrives in a signal-less barangay with their patient list on
 * the device; a guarantee they cannot see the state of is not much of a guarantee. They
 * are also first in the FK-safe push order, so a stuck patient blocks its sessions.
 */
data class PendingSyncCounts(
    val pendingPatients: Int,
    val pendingSessions: Int,
    val pendingSamples: Int,
    val pendingReports: Int,
    val failed: Int,
) {
    /** Total rows still awaiting upload, across all four types. */
    val totalPending: Int
        get() = pendingPatients + pendingSessions + pendingSamples + pendingReports

    /**
     * Rows that have not reached the central database - those still queued and those whose
     * upload already failed. What sign-out warns about and discards, so the two cannot drift.
     */
    val totalUnsynced: Int
        get() = totalPending + failed

    /** True when nothing is pending and nothing has failed. */
    val allSynced: Boolean
        get() = totalPending == 0 && failed == 0
}
