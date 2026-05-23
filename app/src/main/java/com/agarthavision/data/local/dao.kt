package com.agarthavision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.SampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for captured sample records.
 */
@Dao
interface SampleDao {
    /**
     * Inserts a captured sample into the local Room database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SampleEntity)

    /**
     * Returns the most recently captured sample.
     */
    @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSample(): Flow<SampleEntity?>

    /**
     * Returns a sample by its sample identifier.
     */
    @Query("SELECT * FROM samples WHERE sample_id = :sampleId LIMIT 1")
    suspend fun getSampleById(sampleId: String): SampleEntity?
}