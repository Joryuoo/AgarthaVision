package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetSampleDetailUseCaseTest {
    @Test
    fun `loads current user sample and detections`() = runTest {
        val sample = detailSample(userId = "user-1")
        val detection = detailDetection(sampleId = sample.id)
        val useCase = GetSampleDetailUseCase(
            authRepository = DetailAuthRepository(userId = "user-1"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(listOf(detection)),
        )

        val item = useCase(sample.id).first()

        assertEquals(sample, item?.sample)
        assertEquals(listOf(detection), item?.detections)
    }

    @Test
    fun `returns null for another users sample`() = runTest {
        val useCase = GetSampleDetailUseCase(
            authRepository = DetailAuthRepository(userId = "user-2"),
            sampleRepository = DetailSampleRepository(detailSample(userId = "user-1")),
            detectionRepository = DetailDetectionRepository(emptyList()),
        )

        assertNull(useCase("sample-1").first())
    }
}

private class DetailAuthRepository(private val userId: String?) : AuthRepository {
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
}

private class DetailSampleRepository(
    private val sample: Sample?,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(sample?.let(::listOf).orEmpty())
    override suspend fun getSampleById(sampleId: String): Sample? = sample?.takeIf { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>> = flowOf(emptyList())
    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> = emptyList()
    override suspend fun getSamplesPendingSync(userId: String): List<Sample> = emptyList()
}

private class DetailDetectionRepository(
    private val detections: List<Detection>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detections.filter { it.sampleId == sampleId }

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detections.filter { it.sampleId == sampleId })
}

private fun detailSample(userId: String): Sample =
    Sample(
        id = "sample-1",
        userId = userId,
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = "session-1",
        filePath = "/tmp/sample-1.jpg",
        storagePath = "$userId/sample-1.jpg",
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        status = SampleStatus.SYNCED,
    )

private fun detailDetection(sampleId: String): Detection =
    Detection(
        id = "detection-1",
        sampleId = sampleId,
        classLabel = "Ascaris",
        confidence = 0.91f,
        bboxX = 0.1f,
        bboxY = 0.2f,
        bboxW = 0.3f,
        bboxH = 0.4f,
        verdict = DetectionVerdict.CONFIRMED,
        expertClass = null,
        verifiedByUser = true,
    )
