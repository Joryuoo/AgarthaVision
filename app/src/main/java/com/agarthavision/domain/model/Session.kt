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
    /** Cloud sync state; `pending` until the Supabase row exists. Per ADR-007. */
    val supabaseStatus: SessionSyncStatus = SessionSyncStatus.SYNCED,
    /** `true` when opted out of being claimed at the next login. Per ADR-007. */
    val claimExempt: Boolean = false,
) {
    /**
     * Derived link state for the Sessions UI: whether this session is owned, still
     * local-only (unowned or opted out), pending upload, or fully synced.
     */
    val linkState: SessionLinkState
        get() = when {
            userId == null && claimExempt -> SessionLinkState.NOT_LINKED
            userId == null -> SessionLinkState.UNOWNED
            supabaseStatus == SessionSyncStatus.SYNCED -> SessionLinkState.SYNCED
            else -> SessionLinkState.PENDING
        }
}

/**
 * UI-facing link state for a session, derived from ownership and sync status.
 * Per ADR-007.
 */
enum class SessionLinkState {
    /** No owner yet; will be claimed by the next login unless opted out. */
    UNOWNED,

    /** Owner is null and the medtech opted out of claiming; stays local-only. */
    NOT_LINKED,

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
    val totalEpg: Int
)
