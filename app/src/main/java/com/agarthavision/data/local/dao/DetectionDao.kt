package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.DetectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for detections within a verified sample.
 */
@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detection: DetectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetections(detections: List<DetectionEntity>)

    @Query("SELECT * FROM detections WHERE sample_id = :sampleId")
    suspend fun getDetectionsForSample(sampleId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE sample_id = :sampleId")
    fun observeDetectionsForSample(sampleId: String): Flow<List<DetectionEntity>>
}
