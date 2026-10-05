package com.agarthavision.domain.model

/**
 * Report variants. `SESSION` describes one smear; `PATIENT` (14zcqntj2uz, `0015_patient_reports.sql`)
 * pools every included session's findings into one document for a patient. `ADMINISTRATIVE`
 * (cross-patient aggregation) is deferred to Phase 2.
 */
enum class ReportType(val value: String) {
    SESSION("session"),
    PATIENT("patient"),
    ;
    companion object {
        /** Defaults to [SESSION] — the pre-patient-report behaviour for any unrecognised value. */
        fun fromValue(value: String): ReportType =
            entries.firstOrNull { it.value == value } ?: SESSION

        /** Null on an unrecognised value, so a caller can skip the row rather than mislabel it. */
        fun fromValueOrNull(value: String): ReportType? =
            entries.firstOrNull { it.value == value }
    }
}
