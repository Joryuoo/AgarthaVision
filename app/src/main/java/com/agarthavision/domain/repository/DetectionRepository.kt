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
}
