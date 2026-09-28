package com.agarthavision.data.supabase

import android.util.Log
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import javax.inject.Inject

/**
 * Synchronizes one persisted report row from Room to Supabase, together with the files it
 * points at.
 *
 * The files used to stay local while only the metadata round-tripped, which made
 * `pdf_file_path` a lie the moment it crossed a device boundary: it holds a MediaStore id or
 * an absolute path, both of which mean nothing anywhere else. The row travelled and the
 * document did not, so a synced report opened to "No app available". Uploading the bytes to
 * the `reports` bucket is what lets [RestoreReportFilesUseCase] put the document back on the
 * device that needs it.
 */
class SyncReportUseCase @Inject constructor(
    private val reportDao: ReportDao,
    private val remoteDataSource: ReportRemoteDataSource,
    private val reportFileStore: ReportFileStore,
) {
    /**
     * Pushes the report's files and row to Supabase and updates the local sync state.
     *
     * @return [Result.success] when the row reaches [ReportSyncStatus.SYNCED],
     * otherwise [Result.failure] after marking the local row
     * [ReportSyncStatus.SYNC_FAILED].
     */
    suspend operator fun invoke(reportId: String): Result<Unit> {
        val report = reportDao.getReportById(reportId)
            ?: return Result.failure(IllegalArgumentException("Report $reportId does not exist."))

        return runCatching {
            uploadFiles(report)
            remoteDataSource.upsertReport(report)
            reportDao.updateSupabaseStatus(reportId, ReportSyncStatus.SYNCED.value)
        }.onFailure {
            Log.e(TAG, "Sync report failed for $reportId", it)
            reportDao.updateSupabaseStatus(reportId, ReportSyncStatus.SYNC_FAILED.value)
        }
    }

    /**
     * Uploads whichever of the two files this report actually has.
     *
     * A file the device no longer holds is skipped rather than failed. Shared storage is the
     * medtech's to clear, and failing the row over it would park the report in `sync_failed`
     * forever — losing the metadata too, over bytes that are already gone. An upload that
     * *starts* and fails is a different matter and propagates, so the retry is real.
     */
    private suspend fun uploadFiles(report: ReportEntity) {
        uploadIfPresent(
            report.userId,
            report.reportId,
            report.pdfFilePath,
            ReportRemoteDataSource.PDF_EXTENSION,
        )
        uploadIfPresent(
            report.userId,
            report.reportId,
            report.csvFilePath,
            ReportRemoteDataSource.CSV_EXTENSION,
        )
    }

    private suspend fun uploadIfPresent(
        userId: String,
        reportId: String,
        localPath: String?,
        extension: String,
    ) {
        val bytes = localPath?.let { reportFileStore.readBytes(it) }
        if (bytes == null) {
            if (localPath != null) {
                Log.w(TAG, "Report $reportId has no readable .$extension at $localPath")
            }
            return
        }
        remoteDataSource.uploadReportFile(
            ReportRemoteDataSource.objectPathFor(userId, reportId, extension),
            bytes,
        )
    }

    private companion object {
        const val TAG = "SyncReportUseCase"
    }
}
