package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** File paths a report row points at, read before the row is deleted. */
data class ReportFilePaths(
    @ColumnInfo(name = "csv_file_path") val csvFilePath: String?,
    @ColumnInfo(name = "pdf_file_path") val pdfFilePath: String?,
)

/**
 * Removes what already reached Supabase from a phone the server has signed out (14zcqntjph8).
 *
 * **What it removes:** every row that is on the server, whichever account it belongs to. The
 * medtech's patients, smears, verdicts and reports are gone from the phone, and so is anything
 * synced that other accounts left behind on it. All of it comes back at the next sign-in.
 *
 * **What it keeps:** every row that has not reached Supabase, the refused account's included,
 * and the parents those rows need. The server cannot tell this phone why it refused the login,
 * and a password changed on the web looks exactly like a deleted account. Keeping unsynced work
 * means a medtech who only changed their password signs back in and uploads it; an account that
 * really was removed leaves it on a phone nobody can open it on without signing in (C8).
 *
 * Every statement runs inside one transaction in [WipeLocalAccountDataUseCase]. Deepest first,
 * because Room enforces `samples → sessions` and `sessions → patients` as NO ACTION.
 *
 * [WipeLocalAccountDataUseCase]: com.agarthavision.domain.usecase.auth.WipeLocalAccountDataUseCase
 */
@Dao
interface AccountWipeDao {

    @Query("SELECT image_path FROM samples WHERE status = 'synced'")
    suspend fun getSyncedSampleImagePaths(): List<String>

    /** Detections and findings cascade off each sample. */
    @Query("DELETE FROM samples WHERE status = 'synced'")
    suspend fun deleteSyncedSamples(): Int

    @Query("SELECT csv_file_path, pdf_file_path FROM reports WHERE supabase_status = 'synced'")
    suspend fun getSyncedReportFiles(): List<ReportFilePaths>

    @Query("DELETE FROM reports WHERE supabase_status = 'synced'")
    suspend fun deleteSyncedReports(): Int

    /** A session still holding a kept sample or report stays, or its foreign key would fail. */
    @Query(
        """
        DELETE FROM sessions
        WHERE supabase_status = 'synced'
          AND NOT EXISTS (SELECT 1 FROM samples sm WHERE sm.session_id = sessions.session_id)
          AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.session_id = sessions.session_id)
        """,
    )
    suspend fun deleteSyncedSessions(): Int

    /** Links of a deleted patient cascade. A patient a kept session belongs to stays. */
    @Query(
        """
        DELETE FROM patients
        WHERE supabase_status = 'synced'
          AND NOT EXISTS (SELECT 1 FROM sessions s WHERE s.patient_id = patients.patient_id)
        """,
    )
    suspend fun deleteSyncedPatients(): Int
}
