package com.agarthavision.data.local.dao

import androidx.room.Dao
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
        """,
    )
    fun observeReportsForSession(sessionId: String, userId: String): Flow<List<ReportEntity>>

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

    @Query("UPDATE reports SET supabase_status = :status WHERE report_id = :reportId")
    suspend fun updateSupabaseStatus(reportId: String, status: String)
}
