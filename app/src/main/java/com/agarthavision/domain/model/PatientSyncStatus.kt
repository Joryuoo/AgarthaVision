package com.agarthavision.domain.model

/**
 * Per-patient cloud sync state, the fourth of these after [SampleStatus],
 * [SessionSyncStatus] and [ReportSyncStatus]. Consolidation into a shared local sync
 * helper is deferred to the Phase 2 WorkManager work — see CONTEXT.md.
 *
 * Room-only. Never a Supabase column: remote presence is authoritative there.
 *
 * A patient is editable, so a row can return to [PENDING] after having been [SYNCED] — the
 * remote write is an upsert for exactly that reason.
 */
enum class PatientSyncStatus(val value: String) {
    PENDING("pending"),
    SYNCED("synced"),
    SYNC_FAILED("sync_failed"),
    ;

    companion object {
        /** Resolves a stored string to a status, defaulting to [PENDING] when unknown. */
        fun fromValue(value: String): PatientSyncStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}
