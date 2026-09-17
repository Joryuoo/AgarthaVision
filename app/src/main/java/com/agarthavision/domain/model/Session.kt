package com.agarthavision.domain.model

/**
 * Domain model for one capture session. Per ADR-005 a session equals one fecal
 * smear; [label] is the medtech-entered smear name set in the SessionPicker.
 *
 * **A session no longer carries a barangay or a note.** The barangay lives on the patient,
 * because it is the unit surveillance aggregates on and the admin site's geospatial mapping
 * tracks the patient, not the smear — and it does not change from one smear to the next.
 * The note was only ever an ad-hoc patient identifier, which [Patient] now is properly.
 * Both are gone from the Room row, from Supabase, and from here.
 */
data class Session(
    val id: String,
    val userId: String?,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val label: String?,
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
