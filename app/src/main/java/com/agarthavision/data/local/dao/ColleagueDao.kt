package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agarthavision.data.local.entity.ColleagueEntity
import kotlinx.coroutines.flow.Flow

/** Colleagues' names, cached by the pull so read-only records can name their author offline. */
@Dao
interface ColleagueDao {
    /** Writes or refreshes names. A colleague no longer returned keeps the last name known. */
    @Upsert
    suspend fun upsertColleagues(colleagues: List<ColleagueEntity>)

    @Query("SELECT full_name FROM colleagues WHERE user_id = :userId")
    suspend fun getFullName(userId: String): String?

    @Query("SELECT * FROM colleagues")
    fun observeColleagues(): Flow<List<ColleagueEntity>>

    /**
     * Everyone but [userId] who authored a session or a report on this device for a patient
     * [userId] is assigned to: the colleagues whose names the pull fetches (14zcqntjt3p). The
     * same rule as `0008_colleague_names.sql` gives a medtech on the server, read off the rows
     * the device holds rather than off whatever the caller's role can read.
     */
    @Query(
        """
        SELECT s.user_id FROM sessions s
        JOIN patient_users pu ON pu.patient_id = s.patient_id
        WHERE pu.user_id = :userId AND s.user_id IS NOT NULL AND s.user_id <> :userId
        UNION
        SELECT r.user_id FROM reports r
        JOIN sessions s ON s.session_id = r.session_id
        JOIN patient_users pu ON pu.patient_id = s.patient_id
        WHERE pu.user_id = :userId AND r.user_id <> :userId
        """,
    )
    suspend fun getColleagueIdsOnLinkedPatients(userId: String): List<String>
}
