package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for persisted session reports.
 */
interface ReportRepository {
    /**
     * Inserts a freshly-generated report. New rows start with
     * [ReportSyncStatus.PENDING] until [SyncReportUseCase] mirrors them.
     */
    suspend fun insert(report: Report)

    /**
     * Observes reports for [sessionId] / [userId] ordered by `generatedAt` DESC.
     */
    fun observeForSession(sessionId: String, userId: String): Flow<List<Report>>

    /**
     * Loads a single report by id, or `null` if it doesn't exist.
     */
    suspend fun getById(reportId: String): Report?

    /**
     * Returns reports that haven't been successfully synced to Supabase
     * (status `pending` or `sync_failed`) for the given user, oldest first.
     */
    suspend fun getReportsPendingSync(userId: String): List<Report>

    /**
     * Updates the cloud-sync state of a report after a sync attempt.
     */
    suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus)
}
