package com.agarthavision.domain.repository

import com.agarthavision.domain.model.Detection
import kotlinx.coroutines.flow.Flow

interface DetectionRepository {
    suspend fun getDetectionsForSample(sampleId: String): List<Detection>
    fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>>
}
