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
     * Observes the latest captured sample for the given user.
     */
    fun observeLatestSample(userId: String): Flow<Sample?>

    /**
     * Observes all verified samples for the given user, newest first.
     */
    fun observeAllSamples(userId: String): Flow<List<Sample>>

    /**
     * Loads a sample by its identifier.
     */
    suspend fun getSampleById(sampleId: String): Sample?

    /**
     * Observes samples in one session for the given user, newest first.
     */
    fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>>

    /**
     * Loads samples in one session for the given user, newest first.
     */
    suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample>

    /**
     * Returns samples that haven't been successfully synced to Supabase for the given user.
     */
    suspend fun getSamplesPendingSync(userId: String): List<Sample>
}
