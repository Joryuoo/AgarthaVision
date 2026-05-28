package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agarthavision.data.local.entity.SampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for verified samples.
 */
@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SampleEntity)

    @Update
    suspend fun updateSample(sample: SampleEntity)

    @Query("UPDATE samples SET status = :status WHERE sample_id = :sampleId")
    suspend fun updateStatus(sampleId: String, status: String)

    @Query(
        """
        UPDATE samples
        SET status = :status, storage_path = :storagePath
        WHERE sample_id = :sampleId
        """,
    )
    suspend fun updateSyncMetadata(
        sampleId: String,
        status: String,
        storagePath: String,
    )

    @Query("SELECT * FROM samples WHERE user_id = :userId AND status != 'flagged' ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSample(userId: String): Flow<SampleEntity?>

    @Query("SELECT * FROM samples WHERE user_id = :userId AND status != 'flagged' ORDER BY timestamp DESC")
    fun observeAllSamples(userId: String): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE sample_id = :sampleId LIMIT 1")
    suspend fun getSampleById(sampleId: String): SampleEntity?

    @Query("SELECT * FROM samples WHERE session_id = :sessionId AND user_id = :userId AND status != 'flagged' ORDER BY timestamp DESC")
    fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE session_id = :sessionId AND user_id = :userId AND status != 'flagged' ORDER BY timestamp DESC")
    suspend fun getSamplesForSession(sessionId: String, userId: String): List<SampleEntity>

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId AND user_id = :userId AND status = 'flagged'
        ORDER BY timestamp DESC
        """,
    )
    fun observeFlaggedSamplesForSession(sessionId: String, userId: String): Flow<List<SampleEntity>>

    @Query(
        """
        SELECT * FROM samples
        WHERE session_id = :sessionId AND user_id = :userId AND status = 'flagged'
        ORDER BY timestamp DESC
        """,
    )
    suspend fun getFlaggedSamplesForSession(sessionId: String, userId: String): List<SampleEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE session_id = :sessionId AND user_id = :userId AND status = 'flagged'")
    suspend fun countFlaggedForSession(sessionId: String, userId: String): Int

    @Query("UPDATE samples SET is_repeat = NOT is_repeat WHERE sample_id = :sampleId")
    suspend fun toggleIsRepeat(sampleId: String)

    @Query("DELETE FROM samples WHERE sample_id = :sampleId")
    suspend fun deleteSample(sampleId: String)

    @Query("DELETE FROM samples WHERE session_id = :sessionId AND user_id = :userId AND status = 'flagged'")
    suspend fun deleteFlaggedSamplesForSession(sessionId: String, userId: String)

    @Query(
        """
        UPDATE samples
        SET status = :status,
            verified_at = :verifiedAt,
            needs_reannotation = :needsReannotation,
            user_note = :userNote,
            is_repeat = :isRepeat,
            gps_latitude = :gpsLatitude,
            gps_longitude = :gpsLongitude,
            gps_accuracy = :gpsAccuracy,
            predictions_json = NULL
        WHERE sample_id = :sampleId
        """,
    )
    suspend fun updateSampleOnVerify(
        sampleId: String,
        status: String,
        verifiedAt: Long,
        needsReannotation: Boolean,
        userNote: String?,
        isRepeat: Boolean,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAccuracy: Float?,
    )

    @Query(
        """
        SELECT * FROM samples
        WHERE user_id = :userId AND status IN ('verified', 'sync_failed')
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getSamplesPendingSync(userId: String): List<SampleEntity>
}
