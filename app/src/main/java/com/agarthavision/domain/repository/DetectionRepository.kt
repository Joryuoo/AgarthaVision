package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.EggCount
import kotlinx.coroutines.flow.Flow

interface DetectionRepository {
    suspend fun getDetectionsForSample(sampleId: String): List<Detection>
    fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>>

    /**
     * Returns per-species confirmed egg counts for one session, excluding repeat
     * samples.
     */
    suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String): List<EggCount>

    /**
     * Observes confirmed egg counts aggregated by species over a specific time window.
     */
    fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>>

    /**
     * Observes daily egg counts (grouped by sample locally) over a specific time window.
     */
    fun observeDailyEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<DailyEggCount>>
}

data class DailyEggCount(
    val timestamp: Long,
    val count: Int,
)
