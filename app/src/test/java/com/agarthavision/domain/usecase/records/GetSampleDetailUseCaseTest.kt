package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.model.RecordAuthor
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.isReadOnly
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.ColleagueRepository
import com.agarthavision.domain.repository.PatientAccessRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSampleDetailUseCaseTest {
    @Test
    fun `loads current user sample and detections`() = runTest {
        val sample = detailSample(userId = "user-1")
        val detection = detailDetection(sampleId = sample.id)
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            patientAccessRepository = DetailPatientAccess(),
            authRepository = DetailAuthRepository(userId = "user-1"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(listOf(detection)),
        )

        val result = useCase(sample.id).first()

        assertTrue(result is SampleDetailResult.Visible)
        val item = (result as SampleDetailResult.Visible).data
        assertEquals(sample, item.sample)
        assertEquals(listOf(detection), item.detections)
    }

    @Test
    fun `returns NotVisible for another users sample`() = runTest {
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            patientAccessRepository = DetailPatientAccess(),
            authRepository = DetailAuthRepository(userId = "user-2"),
            sampleRepository = DetailSampleRepository(detailSample(userId = "user-1")),
            detectionRepository = DetailDetectionRepository(emptyList()),
        )

        assertEquals(SampleDetailResult.NotVisible, useCase("sample-1").first())
    }

    @Test
    fun `returns NotFound when sample does not exist`() = runTest {
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            patientAccessRepository = DetailPatientAccess(),
            authRepository = DetailAuthRepository(userId = "user-1"),
            sampleRepository = DetailSampleRepository(null),
            detectionRepository = DetailDetectionRepository(emptyList()),
        )

        assertEquals(SampleDetailResult.NotFound, useCase("sample-missing").first())
    }

    @Test
    fun `unowned sample is visible to any user`() = runTest {
        val sample = detailSample(userId = null)
        val detection = detailDetection(sampleId = sample.id)
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            patientAccessRepository = DetailPatientAccess(),
            authRepository = DetailAuthRepository(userId = "user-2"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(listOf(detection)),
        )

        val result = useCase(sample.id).first()

        assertTrue(result is SampleDetailResult.Visible)
    }

    @Test
    fun `a colleague's sample is visible to a medtech assigned to its patient`() = runTest {
        val sample = detailSample(userId = "user-1")
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            authRepository = DetailAuthRepository(userId = "user-2"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(emptyList()),
            patientAccessRepository = DetailPatientAccess(assigned = setOf(sample.sessionId to "user-2")),
        )

        // The patient's history is shared by everyone assigned to it (14zcqntjph5); authorship
        // is unchanged, so the sample still says who wrote it.
        val result = useCase(sample.id).first()

        assertTrue(result is SampleDetailResult.Visible)
        assertEquals("user-1", (result as SampleDetailResult.Visible).data.sample.userId)
    }

    @Test
    fun `a colleague's sample is read-only and names its author`() = runTest {
        val sample = detailSample(userId = "user-1")
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(names = mapOf("user-1" to "Maria Santos")),
            authRepository = DetailAuthRepository(userId = "user-2"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(emptyList()),
            patientAccessRepository = DetailPatientAccess(assigned = setOf(sample.sessionId to "user-2")),
        )

        // 14zcqntjph6: the screen hides every edit and says whose sample it is.
        val result = useCase(sample.id).first() as SampleDetailResult.Visible

        assertEquals(RecordAuthor.Colleague(name = "Maria Santos"), result.author)
        assertTrue(result.author.isReadOnly)
    }

    @Test
    fun `the medtech's own sample stays editable`() = runTest {
        val sample = detailSample(userId = "user-1")
        val useCase = GetSampleDetailUseCase(
            colleagueRepository = DetailColleagues(),
            authRepository = DetailAuthRepository(userId = "user-1"),
            sampleRepository = DetailSampleRepository(sample),
            detectionRepository = DetailDetectionRepository(emptyList()),
            patientAccessRepository = DetailPatientAccess(),
        )

        val result = useCase(sample.id).first() as SampleDetailResult.Visible

        assertEquals(RecordAuthor.Viewer, result.author)
    }
}

private class DetailAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeLocalIdentity(): Flow<com.agarthavision.domain.model.LocalIdentity?> =
        flowOf(userId?.let { com.agarthavision.domain.model.LocalIdentity(userId = it, email = "user@example.com") })
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) =
        PasswordChangeResult.Failed
}

private class DetailSampleRepository(
    private val sample: Sample?,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(sample?.let(::listOf).orEmpty())
    override suspend fun getSampleById(sampleId: String): Sample? = sample?.takeIf { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> = flowOf(emptyList())
    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> = emptyList()
    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()
    override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(emptyList())
}

private class DetailDetectionRepository(
    private val detections: List<Detection>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detections.filter { it.sampleId == sampleId }

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detections.filter { it.sampleId == sampleId })

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?) = emptyList<EggCount>()

    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        flowOf(emptyList())


    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}

private fun detailSample(userId: String?): Sample =
    Sample(
        id = "sample-1",
        userId = userId,
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = "session-1",
        filePath = "/tmp/sample-1.jpg",
        storagePath = userId?.let { "$it/sample-1.jpg" },
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
    )

private class DetailPatientAccess(
    private val assigned: Set<Pair<String, String>> = emptySet(),
) : PatientAccessRepository {
    override suspend fun isAssignedToSessionPatient(sessionId: String, userId: String): Boolean =
        (sessionId to userId) in assigned
}

private class DetailColleagues(
    private val names: Map<String, String?> = emptyMap(),
) : ColleagueRepository {
    override suspend fun nameOf(userId: String): String? = names[userId]
    override fun observeNames(): Flow<Map<String, String?>> = flowOf(names)
}
