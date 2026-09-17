package com.agarthavision.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * The clinical locale every patient date is resolved in.
 *
 * Surveillance is Philippine, so a patient's birthdate and their age are calendar facts in
 * Philippine time, not UTC. Resolving them in UTC would tick an age over at 08:00 local
 * rather than at midnight, and would shift a birthdate entered between midnight and 08:00
 * back a day. `Asia/Manila` has no DST, so this is a fixed +08:00 in practice.
 */
val CLINICAL_ZONE: ZoneId = ZoneId.of("Asia/Manila")

/**
 * Domain model for a patient — the unit the medtech works from.
 *
 * A patient owns many [Session] rows; a session is one fecal smear (ADR-005). Pure Kotlin
 * per C2: `java.time`, never `android.*`.
 *
 * [birthdate] is the stored field rather than an age. An age is wrong the day after it is
 * written, and a clinical report that prints a stale age is a defect nothing downstream
 * catches. [ageYears] recomputes it per encounter instead.
 *
 * [sex] is nullable only to survive reading a row whose stored value is neither `'M'` nor
 * `'F'` — the form requires one, and `patients.sex` is `not null` with a CHECK, so null
 * here means the value on disk was unreadable, never that a medtech left it blank.
 *
 * [psgcBarangayCode] lives here rather than on the session because it is the unit
 * surveillance aggregates on, and it does not change from one smear to the next.
 *
 * [createdBy] is provenance only and grants no access — visibility resolves through the
 * `patient_users` join, mirroring the Supabase RLS policy.
 *
 * There is no delete path. Removing a patient is an admin-side action.
 */
data class Patient(
    val id: String,
    val lastname: String,
    val firstname: String,
    /** Nullable on purpose — many patients do not supply one. */
    val middleName: String? = null,
    /** Null only when the stored value was unreadable. See the class doc. */
    val sex: Sex?,
    val birthdate: LocalDate,
    val psgcBarangayCode: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * `"Cruz, Gerald M."` — surname first, then the given name and a middle initial.
     *
     * The initial appears only when a middle name exists, and a blank or whitespace-only
     * middle name is treated as absent so the result never trails a stray `" ."`.
     */
    val displayName: String
        get() {
            val initial = middleName?.trim()?.firstOrNull()
            return if (initial == null) {
                "$lastname, $firstname"
            } else {
                "$lastname, $firstname $initial."
            }
        }

    /**
     * Completed years of age at [asOf] — computed, never stored.
     *
     * [asOf] is resolved in [CLINICAL_ZONE], the same frame [birthdate] is stored and read
     * in. Resolving the two ends of the comparison in different zones is exactly the
     * off-by-one that prints a wrong age on a clinical report, so both stay in one frame.
     *
     * A birthdate in the future yields `0` rather than a negative age; the form rejects
     * future dates, so this only guards a hand-edited row.
     */
    fun ageYears(asOf: Instant): Int {
        val today = asOf.atZone(CLINICAL_ZONE).toLocalDate()
        if (birthdate.isAfter(today)) return 0
        return Period.between(birthdate, today).years
    }
}
