package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSessionUseCaseTest {
    @Test
    fun `export writes csv with one row per detection`() = runTest {
        val writer = FakeSessionReportRepository()
        val useCase = ExportSessionUseCase(
            authRepository = ExportAuthRepository(userId = "user-1"),
            sampleRepository = ExportSampleRepository(
                listOf(exportSample(id = "sample-1", sessionId = "session-1", userId = "user-1")),
            ),
            detectionRepository = ExportDetectionRepository(
                mapOf(
                    "sample-1" to listOf(
                        exportDetection(sampleId = "sample-1", classLabel = "Ascaris", confidence = 0.91f),
                    ),
                ),
            ),
            sessionReportRepository = writer,
        )

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        assertEquals("/downloads/session-1.csv", result.getOrThrow())
        assertEquals(
            """
            sample_id,captured_at,verified_at,class_label,confidence,gps_lat,gps_lng,gps_accuracy,storage_path
            sample-1,1970-01-01T00:00:01Z,1970-01-01T00:00:02Z,Ascaris,0.91,10.0,20.0,5.0,user-1/sample-1.jpg
            
            """.trimIndent(),
            writer.lastCsv,
        )
    }

    @Test
    fun `export fails when no user is authenticated`() = runTest {
        val useCase = ExportSessionUseCase(
            authRepository = ExportAuthRepository(userId = null),
            sampleRepository = ExportSampleRepository(emptyList()),
            detectionRepository = ExportDetectionRepository(emptyMap()),
            sessionReportRepository = FakeSessionReportRepository(),
        )

        assertTrue(useCase("session-1").isFailure)
    }
}

private class ExportAuthRepository(private val userId: String?) : AuthRepository {
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
}

private class ExportSampleRepository(
    private val samples: List<Sample>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples.filter { it.userId == userId })
    override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>> =
        flowOf(samples.filter { it.sessionId == sessionId && it.userId == userId })

    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> =
        samples.filter { it.sessionId == sessionId && it.userId == userId }

    override suspend fun getSamplesPendingSync(userId: String): List<Sample> = emptyList()
}

private class ExportDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())
}

private class FakeSessionReportRepository : SessionReportRepository {
    var lastCsv: String = ""

    override suspend fun writeSessionCsv(sessionId: String, csv: String): String {
        lastCsv = csv
        return "/downloads/$sessionId.csv"
    }
}

private fun exportSample(id: String, sessionId: String, userId: String): Sample =
    Sample(
        id = id,
        userId = userId,
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = sessionId,
        filePath = "/tmp/$id.jpg",
        storagePath = "$userId/$id.jpg",
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        status = SampleStatus.SYNCED,
    )

private fun exportDetection(sampleId: String, classLabel: String, confidence: Float): Detection =
    Detection(
        id = "detection-$sampleId",
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = 0.1f,
        bboxY = 0.2f,
        bboxW = 0.3f,
        bboxH = 0.4f,
        verdict = DetectionVerdict.CONFIRMED,
        expertClass = null,
        verifiedByUser = true,
    )
