package com.agarthavision.domain.model

/**
 * Per-report cloud sync state. Mirrors the lifecycle of `samples.status` for
 * the verified→synced transition, scoped to the reports table.
 */
enum class ReportSyncStatus(val value: String) {
    PENDING("pending"),
    SYNCED("synced"),
    SYNC_FAILED("sync_failed"),
    ;
    companion object {
        fun fromValue(value: String): ReportSyncStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}
