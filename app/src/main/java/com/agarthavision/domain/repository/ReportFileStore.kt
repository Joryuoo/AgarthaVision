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

    /**
     * Reads back a file previously written by [writeCsv] or [writePdf].
     *
     * [path] is the opaque string those two return, so both shapes are handled: a MediaStore
     * `content://` URI (API 29+) and an absolute filesystem path (API 26-28).
     *
     * @return the bytes, or null when the file is no longer on this device — which is an
     *   ordinary outcome, not a failure: shared storage is the medtech's to clear, and a
     *   report whose file is gone is exactly the case the storage round-trip exists for.
     */
    suspend fun readBytes(path: String): ByteArray?

    /**
     * Deletes a file previously written by [writeCsv] or [writePdf], either shape.
     *
     * Only for an account the server no longer accepts (14zcqntjph8): a report names its
     * patient, and these files sit in shared storage where any file manager can open them.
     *
     * @return true when a file was removed; false when it was already gone or could not be.
     */
    suspend fun delete(path: String): Boolean
}
