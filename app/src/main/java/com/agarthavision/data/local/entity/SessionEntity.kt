package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one fecal smear.
 *
 * Mirrors the Supabase `sessions` table in `supabase/migrations/0001_init.sql`. One row
 * per `startSession()` call. A session belongs to a [PatientEntity] and never ends.
 *
 * Three columns that existed up to Room 12 are deliberately gone:
 * - `ended_at` — sessions never end (86d4ab4vm). Nothing had written it since that
 *   ticket, and a column no writer sets is a trap: `ended_at IS NULL` silently matches
 *   every row while still reading like a filter.
 * - `notes` — it was being used as an ad-hoc patient identifier. [PatientEntity] is what
 *   replaces it.
 * - `psgc_barangay_code` — moved to the patient, which is the unit surveillance
 *   aggregates on and which does not change from one smear to the next.
 *
 * `claim_exempt` is gone too: login is mandatory on first run, so every row has an owner
 * from the moment it is created and the deferred-claim axis it served has nothing left
 * to do.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
        ),
    ],
    indices = [Index("patient_id")],
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String?,

    /**
     * The patient this smear belongs to. A session is always created from a patient's
     * session list, so the patient is known at creation and this is never null.
     */
    @ColumnInfo(name = "patient_id")
    val patientId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /**
     * The smear label, auto-generated as `C.G.-0730600000-001` and editable thereafter.
     * Cosmetic and deliberately not unique — the session id is the real key.
     */
    @ColumnInfo(name = "label")
    val label: String? = null,

    /**
     * Room-only cloud sync state. Per ADR-007, a session is written locally first and
     * pushed best-effort; `pending` until the Supabase row exists. Never a Supabase
     * column — remote presence is authoritative there.
     */
    @ColumnInfo(name = "supabase_status", defaultValue = "'synced'")
    val supabaseStatus: String = "synced",
)
