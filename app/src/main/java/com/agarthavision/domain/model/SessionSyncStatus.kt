package com.agarthavision.domain.model

/**
 * Per-session cloud sync state, mirroring [ReportSyncStatus] for the local→remote
 * push of the `sessions` row.
 *
 * Per ADR-007, a session is now written to Room first ([PENDING]) and pushed to
 * Supabase best-effort; it only becomes [SYNCED] once the remote row exists.
 *
 * Note: this is the third `*SyncStatus` enum ([SampleStatus], [ReportSyncStatus]).
 * Consolidation into a shared local sync helper is deferred to the Phase 2
 * WorkManager work — see CONTEXT.md.
 */
enum class SessionSyncStatus(val value: String) {
    PENDING("pending"),
    SYNCED("synced"),
    SYNC_FAILED("sync_failed"),
    ;

    companion object {
        /** Resolves a stored string to a status, defaulting to [PENDING] when unknown. */
        fun fromValue(value: String): SessionSyncStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}
