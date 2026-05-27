package com.agarthavision.domain.repository

/**
 * Writes generated session reports to device storage.
 */
interface SessionReportRepository {
    /**
     * Persists [csv] for [sessionId] and returns the saved file path or URI string.
     */
    suspend fun writeSessionCsv(sessionId: String, csv: String): String
}
