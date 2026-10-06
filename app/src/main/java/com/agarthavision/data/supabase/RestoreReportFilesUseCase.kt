package com.agarthavision.data.supabase

import com.agarthavision.core.util.Logger
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.domain.repository.ReportFileStore
import javax.inject.Inject

/**
 * Where a report's PDF lives on this device after [RestoreReportFilesUseCase] has run.
 *
 * Null when the report predates the `reports` bucket, or nothing could be recovered for it.
 */
data class RestoredReportFiles(
    val pdfFilePath: String?,
)

/**
 * Puts a report's PDF back on this device by pulling it from the `reports` bucket.
 *
 * The counterpart to [SyncReportUseCase]'s upload. A report row syncs between devices but
 * `pdf_file_path` is device-local — a MediaStore id or an absolute path — so on any device but
 * the one that generated it, the row names a file that was never there. This downloads the
 * stored bytes, writes them through [ReportFileStore] so they land in `Documents/AgarthaVision/`
 * like any other report, and repoints the row at the result.
 *
 * Writing the path back is what keeps this a one-time cost: the next open is a local read.
 *
 * `csv_file_path` is untouched here — the CSV format is retired (PDF-only reports), so a legacy
 * CSV-only report has nothing to restore and its stored path just passes through unchanged.
 */
class RestoreReportFilesUseCase @Inject constructor(
    private val reportDao: ReportDao,
    private val remoteDataSource: ReportRemoteDataSource,
    private val reportFileStore: ReportFileStore,
) {
    /**
     * Ensures the report's PDF is readable locally, downloading it when missing.
     *
     * Already present is left alone rather than re-fetched — this runs on a tap, and
     * re-downloading a document the device already has would spend the medtech's data to
     * arrive at the same place.
     *
     * @return the local PDF path, or [Result.failure] when the report is unknown or nothing
     *   could be recovered for it (including a legacy CSV-only report, which has no PDF to
     *   restore).
     */
    suspend operator fun invoke(reportId: String): Result<RestoredReportFiles> {
        val report = reportDao.getReportById(reportId)
            ?: return Result.failure(IllegalArgumentException("Report $reportId does not exist."))

        val pdfPath = ensureLocal(
            userId = report.userId,
            reportId = reportId,
            sessionId = report.sessionId,
            patientId = report.patientId,
            localPath = report.pdfFilePath,
        )

        return if (pdfPath == null) {
            Result.failure(
                IllegalStateException("No stored PDF could be recovered for report $reportId."),
            )
        } else {
            // Only touch the row when the path actually changed; a no-op write would bump the
            // row for nothing and, on a shared session, race the sync that is reading it.
            if (pdfPath != report.pdfFilePath) {
                reportDao.updateFilePaths(reportId, pdfPath, report.csvFilePath)
            }
            Result.success(RestoredReportFiles(pdfFilePath = pdfPath))
        }
    }

    /**
     * A null [localPath] means the report was never generated as a PDF (a legacy CSV-only
     * report), so nothing was uploaded for it either — asking Storage would only spend a round
     * trip on a certain miss.
     *
     * @return a path whose bytes this device can read, or null when the file is neither here
     *   nor in Storage.
     */
    private suspend fun ensureLocal(
        userId: String,
        reportId: String,
        sessionId: String?,
        patientId: String?,
        localPath: String?,
    ): String? {
        if (localPath == null || reportFileStore.readBytes(localPath) != null) return localPath

        val objectPath = ReportRemoteDataSource.objectPathFor(userId, reportId, ReportRemoteDataSource.PDF_EXTENSION)
        val bytes = runCatching { remoteDataSource.downloadReportFile(objectPath) }
            .onFailure { Logger.w(TAG, "No stored pdf for report $reportId", it) }
            .getOrNull()

        return bytes?.let {
            runCatching {
                if (patientId != null) {
                    reportFileStore.writePatientPdf(reportId, patientId, it)
                } else {
                    reportFileStore.writePdf(reportId, requireNotNull(sessionId), it)
                }
            }
                .onFailure { e -> Logger.e(TAG, "Could not write restored pdf for $reportId", e) }
                .getOrNull()
        }
    }

    private companion object {
        const val TAG = "RestoreReportFiles"
    }
}
