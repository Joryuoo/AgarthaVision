package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Sample
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for captured sample persistence.
 */
interface SampleRepository {
    /**
     * Saves a captured sample locally.
     */
    suspend fun saveSample(sample: Sample)

    /**
     * Observes the latest captured sample.
     */
    fun observeLatestSample(): Flow<Sample?>

    /**
     * Loads a sample by its identifier.
     */
    suspend fun getSampleById(sampleId: String): Sample?
}
