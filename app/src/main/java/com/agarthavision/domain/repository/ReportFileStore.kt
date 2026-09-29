package com.agarthavision.domain.repository

/**
 * Writes the on-device PDF file for a generated report.
 *
 * Phase 1 implementation drops the file into the Downloads directory. Keyed by
 * `reportId` so multiple reports for the same session don't collide.
 */
interface ReportFileStore {
    /**
     * Persists the PDF body and returns the absolute path (or `content://` URI) to the
     * written file.
     */
    suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String

    /**
     * Persists a patient-scoped report's PDF body and returns the absolute path (or
     * `content://` URI) to the written file. Keyed by `reportId`/`patientId` so multiple
     * patient reports for the same patient don't collide.
     */
    suspend fun writePatientPdf(reportId: String, patientId: String, pdf: ByteArray): String

    /**
     * Reads back a file previously written by [writePdf].
     *
     * [path] is the opaque string it returns, so both shapes are handled: a MediaStore
     * `content://` URI (API 29+) and an absolute filesystem path (API 26-28).
     *
     * @return the bytes, or null when the file is no longer on this device — which is an
     *   ordinary outcome, not a failure: shared storage is the medtech's to clear, and a
     *   report whose file is gone is exactly the case the storage round-trip exists for.
     */
    suspend fun readBytes(path: String): ByteArray?
}
