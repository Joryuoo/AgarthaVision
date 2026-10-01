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
 * Removes the clinical data of an account the server no longer accepts (14zcqntjph8).
 *
 * **What it removes:** every row that already reached Supabase, and every row of the refused
 * account whatever its state. So the deactivated medtech's patients, smears, verdicts and
 * reports are gone from the phone, and so is anything synced that other accounts left behind
 * on it — all of it is on the server and comes back at the next sign-in.
 *
 * **What it keeps:** another account's rows that have not reached Supabase, and the parents
 * they need. A shared phone where a colleague signed in, worked offline and was replaced by the
 * refused medtech without signing out still holds that colleague's unsynced work, and wiping
 * it would discard clinical work that is not the refused account's to lose (C8).
 *
 * Run [com.agarthavision.domain.usecase.auth.DiscardUnsyncedDataUseCase] first: it removes the
 * refused account's own unsynced subtree and counts it, which is what the login screen reports.
 *
 * Every statement runs inside one transaction in [WipeLocalAccountDataUseCase]. Deepest first,
 * because Room enforces `samples → sessions` and `sessions → patients` as NO ACTION.
 *
 * [WipeLocalAccountDataUseCase]: com.agarthavision.domain.usecase.auth.WipeLocalAccountDataUseCase
 */
@Dao
interface AccountWipeDao {

    @Query("SELECT image_path FROM samples WHERE $SAMPLE_WIPED")
    suspend fun getWipedSampleImagePaths(userId: String): List<String>

    /** Detections and findings cascade off each sample. */
    @Query("DELETE FROM samples WHERE $SAMPLE_WIPED")
    suspend fun deleteSamples(userId: String): Int

    @Query("SELECT csv_file_path, pdf_file_path FROM reports WHERE $REPORT_WIPED")
    suspend fun getWipedReportFiles(userId: String): List<ReportFilePaths>

    @Query("DELETE FROM reports WHERE $REPORT_WIPED")
    suspend fun deleteReports(userId: String): Int

    /** A session still holding a kept sample or report stays, or its foreign key would fail. */
    @Query(
        """
        DELETE FROM sessions
        WHERE (supabase_status = 'synced' OR user_id = :userId OR user_id IS NULL)
          AND NOT EXISTS (SELECT 1 FROM samples sm WHERE sm.session_id = sessions.session_id)
          AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.session_id = sessions.session_id)
        """,
    )
    suspend fun deleteSessions(userId: String): Int

    @Query("DELETE FROM patient_users WHERE user_id = :userId")
    suspend fun deletePatientLinks(userId: String): Int

    /** Links of a deleted patient cascade. A patient a kept session belongs to stays. */
    @Query(
        """
        DELETE FROM patients
        WHERE (supabase_status = 'synced' OR created_by = :userId)
          AND NOT EXISTS (SELECT 1 FROM sessions s WHERE s.patient_id = patients.patient_id)
        """,
    )
    suspend fun deletePatients(userId: String): Int
}

// Flagged frames are drafts the C8 local exception lets the app discard, and a null owner is a
// frame nobody can claim any more once the identity is gone.
private const val SAMPLE_WIPED = "status = 'synced' OR user_id = :userId OR user_id IS NULL"
private const val REPORT_WIPED = "supabase_status = 'synced' OR user_id = :userId"
