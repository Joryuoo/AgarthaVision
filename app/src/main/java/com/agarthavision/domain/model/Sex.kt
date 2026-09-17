package com.agarthavision.domain.model

/**
 * A patient's sex, as recorded for surveillance stratification.
 *
 * Deliberately two values. DOH and WHO STH surveillance data is stratified this way, and
 * the `patients.sex` CHECK in `supabase/migrations/0001_init.sql` admits only `'M'` or
 * `'F'`; a third value here would sync and then be rejected by the server.
 */
enum class Sex(val remoteValue: String) {
    MALE("M"),
    FEMALE("F"),
    ;

    companion object {
        /**
         * Reads a stored value back, **totally**.
         *
         * A row hand-edited in the Supabase dashboard can carry anything, and the patient
         * list must still render. An unrecognised value falls back to [MALE] rather than
         * throwing — a wrong glyph on one row is recoverable, a crashing list is not.
         * Matching is case-insensitive so `'m'` survives the round trip.
         */
        fun fromRemote(value: String): Sex =
            entries.firstOrNull { it.remoteValue.equals(value, ignoreCase = true) } ?: MALE
    }
}
