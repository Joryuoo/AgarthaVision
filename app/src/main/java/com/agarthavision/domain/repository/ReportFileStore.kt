package com.agarthavision.domain.repository

/**
 * Writes the on-device CSV file for a generated report.
 *
 * Phase 1 implementation drops the file into the Downloads directory. Keyed by
 * `reportId` so multiple reports for the same session don't collide.
 */
interface ReportFileStore {
    /**
     * Persists the CSV body and returns the absolute path to the written file.
     */
    suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String

    /**
     * Persists the PDF body and returns the absolute path (or `content://` URI) to the
     * written file, mirroring [writeCsv].
     */
    suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String
}
