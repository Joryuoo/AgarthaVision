package com.agarthavision.domain.model

/**
 * Phase 1 report variants. Only `SESSION` is supported today; `ADMINISTRATIVE`
 * (cross-session aggregation) is deferred to Phase 2.
 */
enum class ReportType(val value: String) {
    SESSION("session"),
    ;
    companion object {
        fun fromValue(value: String): ReportType =
            entries.firstOrNull { it.value == value } ?: SESSION
    }
}
