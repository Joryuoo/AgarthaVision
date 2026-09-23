@file:Suppress("TooManyFunctions", "LongParameterList")

package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for persisted session reports.
 */
@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Query(
        """
        SELECT * FROM reports
        WHERE session_id = :sessionId AND user_id = :userId
        ORDER BY generated_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeReportsForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>>

    /**
     * Live count of all reports for [sessionId] / [userId], independent of any page limit —
     * lets the UI show "showing N of total" and offer larger page sizes.
     */
    @Query("SELECT COUNT(*) FROM reports WHERE session_id = :sessionId AND user_id = :userId")
    fun observeReportCountForSession(sessionId: String, userId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM reports
        WHERE user_id = :userId
        ORDER BY generated_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeAllReports(
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>>

    @Query("SELECT COUNT(*) FROM reports WHERE user_id = :userId")
    fun observeAllReportsCount(userId: String): Flow<Int>

    @Query(
        """
        SELECT r.*, s.label AS session_label
        FROM reports r
        LEFT JOIN sessions s ON r.session_id = s.session_id
        WHERE r.user_id = :userId
          AND (:startMillis IS NULL OR r.generated_at >= :startMillis)
          AND (:endMillis IS NULL OR r.generated_at <= :endMillis)
          AND (:species IS NULL OR r.positive_species_json LIKE '%' || :species || '%')
          AND (
            :query = ''
            OR r.report_id LIKE '%' || :query || '%' ESCAPE '\'
            OR r.positive_species_json LIKE '%' || :query || '%' ESCAPE '\'
            OR r.session_id LIKE '%' || :query || '%' ESCAPE '\'
            OR (s.label IS NOT NULL AND s.label LIKE '%' || :query || '%' ESCAPE '\')
          )
        ORDER BY r.generated_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeFilteredReports(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String = "",
        limit: Int,
        offset: Int,
    ): Flow<List<ReportWithSessionLabel>>

    @Query(
        """
        SELECT COUNT(*)
        FROM reports r
        LEFT JOIN sessions s ON r.session_id = s.session_id
        WHERE r.user_id = :userId
          AND (:startMillis IS NULL OR r.generated_at >= :startMillis)
          AND (:endMillis IS NULL OR r.generated_at <= :endMillis)
          AND (:species IS NULL OR r.positive_species_json LIKE '%' || :species || '%')
          AND (
            :query = ''
            OR r.report_id LIKE '%' || :query || '%' ESCAPE '\'
            OR r.positive_species_json LIKE '%' || :query || '%' ESCAPE '\'
            OR r.session_id LIKE '%' || :query || '%' ESCAPE '\'
            OR (s.label IS NOT NULL AND s.label LIKE '%' || :query || '%' ESCAPE '\')
          )
        """,
    )
    fun observeFilteredReportsCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String = "",
    ): Flow<Int>

    @Query("SELECT * FROM reports WHERE report_id = :reportId LIMIT 1")
    suspend fun getReportById(reportId: String): ReportEntity?

    @Query(
        """
        SELECT * FROM reports
        WHERE user_id = :userId AND supabase_status IN ('pending', 'sync_failed')
        ORDER BY generated_at ASC
        """,
    )
    suspend fun getReportsPendingSync(userId: String): List<ReportEntity>

    /**
     * Hard-deletes a report. Called only for one that never reached Supabase, when its author
     * signs out — see `DiscardUnsyncedDataUseCase`. Outside C8's scope: a report is a rendering
     * of findings, not the findings themselves, and nothing hangs off this row.
     */
    @Query("DELETE FROM reports WHERE report_id = :reportId")
    suspend fun deleteReport(reportId: String)

    @Query("UPDATE reports SET supabase_status = :status WHERE report_id = :reportId")
    suspend fun updateSupabaseStatus(reportId: String, status: String)

    /**
     * Repoints a report at the files it now has on *this* device, after they were restored
     * from Storage.
     *
     * The paths are device-local by nature — a MediaStore id or an absolute path — so the
     * row arriving from another device always names a file this one does not have. Writing
     * the local path back is what makes the second open a plain local read.
     */
    @Query(
        """
        UPDATE reports
        SET pdf_file_path = :pdfFilePath, csv_file_path = :csvFilePath
        WHERE report_id = :reportId
        """,
    )
    suspend fun updateFilePaths(reportId: String, pdfFilePath: String?, csvFilePath: String?)

    /**
     * Live count of owned reports still awaiting cloud upload (`pending` only, not
     * `sync_failed`). Drives the Settings Data & Sync section. Per ADR-007.
     */
    @Query("SELECT COUNT(*) FROM reports WHERE user_id = :userId AND supabase_status = 'pending'")
    fun observePendingCount(userId: String): Flow<Int>

    /**
     * Live count of owned reports whose last sync attempt failed. Drives the Settings
     * Data & Sync section. Per ADR-007.
     */
    @Query("SELECT COUNT(*) FROM reports WHERE user_id = :userId AND supabase_status = 'sync_failed'")
    fun observeFailedCount(userId: String): Flow<Int>

    /**
     * Claims reports belonging to the given sessions for [userId], marking them pending
     * sync. Only touches currently-unowned rows so it is idempotent. Per ADR-007.
     */
    @Query(
        """
        UPDATE reports
        SET user_id = :userId, supabase_status = 'pending'
        WHERE session_id IN (:sessionIds) AND user_id IS NULL
        """,
    )
    suspend fun claimReportsForSessions(sessionIds: List<String>, userId: String)
}

/**
 * Report projection including the session label from joined sessions.
 */
data class ReportWithSessionLabel(
    @Embedded val report: ReportEntity,
    @ColumnInfo(name = "session_label") val sessionLabel: String? = null,
)
