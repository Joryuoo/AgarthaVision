package com.agarthavision.domain.model

/**
 * Domain model for one capture session. Per ADR-005 a session equals one fecal
 * smear; [label] is the medtech-entered smear name set in the SessionPicker.
 */
data class Session(
    val id: String,
    val userId: String?,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val notes: String?,
    val label: String?,
    /**
     * The patient's barangay as a canonical zero-padded 10-digit PSGC code, or null for
     * sessions created before the picker existed. The unit of analysis for surveillance
     * mapping. The per-sample GPS fix is gone as of Room 13 — it recorded where the smear
 * was read, not where the infection came from.
     */
    val psgcBarangayCode: String? = null,
    /** Cloud sync state; `pending` until the Supabase row exists. Per ADR-007. */
    val supabaseStatus: SessionSyncStatus = SessionSyncStatus.SYNCED,
) {
    /**
     * Derived link state for the Sessions UI: whether this session is awaiting upload or
     * fully synced.
     *
     * There is no unowned state any more. Login is mandatory on first run, so a session
     * has an owner from the moment it is created.
     */
    val linkState: SessionLinkState
        get() = when (supabaseStatus) {
            SessionSyncStatus.SYNCED -> SessionLinkState.SYNCED
            else -> SessionLinkState.PENDING
        }
}

/**
 * UI-facing sync state for a session.
 *
 * `UNOWNED` and `NOT_LINKED` are gone: mandatory first-run login means every session has
 * an owner when it is created, so the whole unowned axis — and the deferred-claim
 * machinery that served it — has nothing left to represent.
 */
enum class SessionLinkState {
    /** Owned and awaiting cloud upload. */
    PENDING,

    /** Owned and confirmed present in Supabase. */
    SYNCED,
}

/**
 * Domain model wrapping a session with its aggregated metrics.
 */
data class SessionWithStats(
    val session: Session,
    val totalSamples: Int,
    val verifiedSamples: Int,
    /** Frames awaiting review, excluding repeats — what blocks ending the session. */
    val unverifiedSamples: Int,
    val totalEpg: Int
)
