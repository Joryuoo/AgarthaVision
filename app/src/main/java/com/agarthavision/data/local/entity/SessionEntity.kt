package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a capture session.
 *
 * Mirrors the Supabase `sessions` table. One row per `startSession()` call;
 * `endedAt` is set on `stopSession()`. See CONTEXT.md.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String?,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "label")
    val label: String? = null,

    /**
     * The patient's barangay as a canonical zero-padded 10-digit PSGC code, or null for
     * sessions created before the picker existed. Mirrors `sessions.psgc_barangay_code`
     * (`supabase/migrations/0010_session_psgc_barangay.sql`).
     *
     * Barangay level only — the code resolves upward to city/municipality, province and
     * region on its own. This is the key the surveillance map aggregates on; the
     * capture-time GPS fix on each sample stays provenance only.
     */
    @ColumnInfo(name = "psgc_barangay_code")
    val psgcBarangayCode: String? = null,

    /**
     * Room-only cloud sync state. Per ADR-007, a session is now written locally first
     * and pushed best-effort; `pending` until the Supabase row exists. Never a Supabase
     * column — remote presence is authoritative there. Defaults `synced` on migration so
     * pre-feature rows (whose remote insert already succeeded) are not re-pushed.
     */
    @ColumnInfo(name = "supabase_status", defaultValue = "'synced'")
    val supabaseStatus: String = "synced",

    /**
     * `true` when the medtech has opted this session out of being claimed at the next
     * login (the per-session "Don't link to account" toggle). Claim-exempt sessions stay
     * local-only and are excluded from sync. **Room-only.** Per ADR-007.
     */
    @ColumnInfo(name = "claim_exempt", defaultValue = "0")
    val claimExempt: Boolean = false,
)
