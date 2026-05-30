package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.domain.model.ReportSyncStatus
import javax.inject.Inject

/**
 * Synchronizes one persisted report row from Room to Supabase.
 *
 * Row-only sync — the CSV file at `csv_file_path` stays local; only the
 * metadata + aggregate stats round-trip. Mirrors the pattern of
 * [SyncSampleUseCase] without the file-upload step.
 */
class SyncReportUseCase @Inject constructor(
    private val reportDao: ReportDao,
    private val remoteDataSource: ReportRemoteDataSource,
) {
    /**
     * Pushes the report row to Supabase and updates the local sync state.
     *
     * @return [Result.success] when the row reaches [ReportSyncStatus.SYNCED],
     * otherwise [Result.failure] after marking the local row
     * [ReportSyncStatus.SYNC_FAILED].
     */
    suspend operator fun invoke(reportId: String): Result<Unit> {
        val report = reportDao.getReportById(reportId)
            ?: return Result.failure(IllegalArgumentException("Report $reportId does not exist."))

        return runCatching {
            remoteDataSource.upsertReport(report)
            reportDao.updateSupabaseStatus(reportId, ReportSyncStatus.SYNCED.value)
        }.onFailure {
            reportDao.updateSupabaseStatus(reportId, ReportSyncStatus.SYNC_FAILED.value)
        }
    }
}
