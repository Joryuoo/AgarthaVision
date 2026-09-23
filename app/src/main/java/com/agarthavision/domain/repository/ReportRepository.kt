@file:Suppress("LongParameterList")

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
     * Observes one page of reports for [sessionId] / [userId] ordered by `generatedAt` DESC —
     * [limit] rows starting at [offset].
     */
    fun observeForSession(sessionId: String, userId: String, limit: Int, offset: Int): Flow<List<Report>>

    /**
     * Observes the total number of reports for [sessionId] / [userId], ignoring any page limit.
     */
    fun observeCountForSession(sessionId: String, userId: String): Flow<Int>

    /**
     * Observes one page of all reports for [userId] ordered by `generatedAt` DESC.
     */
    fun observeAll(userId: String, limit: Int, offset: Int): Flow<List<Report>>

    /**
     * Observes the total number of reports for [userId], ignoring any page limit.
     */
    fun observeAllCount(userId: String): Flow<Int>

    /**
     * Observes filtered reports across all sessions for [userId].
     */
    fun observeFiltered(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String = "",
        limit: Int,
        offset: Int,
    ): Flow<List<Report>>

    /**
     * Observes the total number of filtered reports for [userId].
     */
    fun observeFilteredCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String = "",
    ): Flow<Int>

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
