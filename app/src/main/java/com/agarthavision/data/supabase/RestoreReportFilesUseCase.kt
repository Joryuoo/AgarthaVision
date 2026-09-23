package com.agarthavision.data.supabase

import android.util.Log
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.domain.repository.ReportFileStore
import javax.inject.Inject

/**
 * Where a report's files live on this device after [RestoreReportFilesUseCase] has run.
 *
 * Either may be null: a report is generated as one format or the other, and a report written
 * before the `reports` bucket existed has nothing stored to recover.
 */
data class RestoredReportFiles(
    val pdfFilePath: String?,
    val csvFilePath: String?,
)

/**
 * Puts a report's files back on this device by pulling them from the `reports` bucket.
 *
 * The counterpart to [SyncReportUseCase]'s upload. A report row syncs between devices but
 * `pdf_file_path` and `csv_file_path` are device-local — a MediaStore id or an absolute path
 * — so on any device but the one that generated it, the row names a file that was never
 * there. This downloads the stored bytes, writes them through [ReportFileStore] so they land
 * in `Documents/AgarthaVision/` like any other report, and repoints the row at the result.
 *
 * Writing the path back is what keeps this a one-time cost: the next open is a local read.
 */
class RestoreReportFilesUseCase @Inject constructor(
    private val reportDao: ReportDao,
    private val remoteDataSource: ReportRemoteDataSource,
    private val reportFileStore: ReportFileStore,
) {
    /**
     * Ensures the report's files are readable locally, downloading whatever is missing.
     *
     * Files already present are left alone rather than re-fetched — this runs on a tap, and
     * re-downloading a document the device already has would spend the medtech's data to
     * arrive at the same place.
     *
     * @return the local paths, or [Result.failure] when the report is unknown or nothing
     *   could be recovered for it.
     */
    suspend operator fun invoke(reportId: String): Result<RestoredReportFiles> {
        val report = reportDao.getReportById(reportId)
            ?: return Result.failure(IllegalArgumentException("Report $reportId does not exist."))

        val pdfPath = ensureLocal(
            userId = report.userId,
            reportId = reportId,
            sessionId = report.sessionId,
            localPath = report.pdfFilePath,
            extension = ReportRemoteDataSource.PDF_EXTENSION,
        )
        val csvPath = ensureLocal(
            userId = report.userId,
            reportId = reportId,
            sessionId = report.sessionId,
            localPath = report.csvFilePath,
            extension = ReportRemoteDataSource.CSV_EXTENSION,
        )

        return if (pdfPath == null && csvPath == null) {
            Result.failure(
                IllegalStateException("No stored file could be recovered for report $reportId."),
            )
        } else {
            // Only touch the row when a path actually changed; a no-op write would bump the row
            // for nothing and, on a shared session, race the sync that is reading it.
            if (pdfPath != report.pdfFilePath || csvPath != report.csvFilePath) {
                reportDao.updateFilePaths(reportId, pdfPath, csvPath)
            }
            Result.success(RestoredReportFiles(pdfFilePath = pdfPath, csvFilePath = csvPath))
        }
    }

    /**
     * A null [localPath] means the report was never generated in this format, so nothing was
     * uploaded for it either — asking Storage would only spend a round trip on a certain miss.
     *
     * @return a path whose bytes this device can read, or null when the file is neither here
     *   nor in Storage.
     */
    @Suppress("LongParameterList")
    private suspend fun ensureLocal(
        userId: String,
        reportId: String,
        sessionId: String,
        localPath: String?,
        extension: String,
    ): String? {
        if (localPath == null || reportFileStore.readBytes(localPath) != null) return localPath

        val objectPath = ReportRemoteDataSource.objectPathFor(userId, reportId, extension)
        val bytes = runCatching { remoteDataSource.downloadReportFile(objectPath) }
            .onFailure { Log.w(TAG, "No stored $extension for report $reportId", it) }
            .getOrNull()

        return bytes?.let {
            runCatching {
                if (extension == ReportRemoteDataSource.PDF_EXTENSION) {
                    reportFileStore.writePdf(reportId, sessionId, it)
                } else {
                    reportFileStore.writeCsv(reportId, sessionId, it.decodeToString())
                }
            }.onFailure { e -> Log.e(TAG, "Could not write restored $extension for $reportId", e) }
                .getOrNull()
        }
    }

    private companion object {
        const val TAG = "RestoreReportFiles"
    }
}
