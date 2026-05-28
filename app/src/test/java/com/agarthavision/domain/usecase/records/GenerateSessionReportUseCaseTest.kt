package com.agarthavision.domain.usecase.records

import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateSessionReportUseCaseTest {
    @Test
    fun `generates report, writes csv, and persists metadata`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1"),
            sessionRepository = ReportSessionRepository(session = reportSession("session-1", "user-1")),
            sampleRepository = ReportSampleRepository(
                samples = listOf(
                    reportSample(id = "sample-1", sessionId = "session-1", userId = "user-1", isRepeat = false),
                    reportSample(id = "sample-2", sessionId = "session-1", userId = "user-1", isRepeat = true),
                ),
            ),
            detectionRepository = ReportDetectionRepository(
                detectionsBySample = mapOf(
                    "sample-1" to listOf(reportDetection("sample-1", "Ascaris", 0.91f)),
                ),
                eggCounts = listOf(
                    EggCount("Ascaris", 2),
                    EggCount("Trichuris trichiura", 1),
                ),
            ),
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportCsvBuilder = ReportCsvBuilder(),
            syncReportUseCase = noOpSyncReportUseCase(),
        )

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(report.id, reportFileStore.lastReportId)
        assertEquals(report.id, reportRepository.lastInserted?.id)
        assertEquals("session-1", report.sessionId)
        assertEquals("user-1", report.userId)
        assertEquals(ReportType.SESSION, report.reportType)
        assertEquals(1, report.totalSamples)
        assertEquals(3, report.totalEggsConfirmed)
        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura"), report.positiveSpecies)
        assertEquals(48, report.epgPerSpecies["Ascaris lumbricoides"])
        assertEquals(24, report.epgPerSpecies["Trichuris trichiura"])
        assertEquals("/downloads/report.csv", report.csvFilePath)
        assertEquals(ReportSyncStatus.PENDING, report.supabaseStatus)
        assertNotNull(report.generatedAt)
        assertTrue(reportFileStore.lastCsv.contains("# report_id: ${report.id}"))
    }

    @Test
    fun `fails when no user is authenticated`() = runTest {
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = null),
            sessionRepository = ReportSessionRepository(session = null),
            sampleRepository = ReportSampleRepository(samples = emptyList()),
            detectionRepository = ReportDetectionRepository(detectionsBySample = emptyMap(), eggCounts = emptyList()),
            reportRepository = FakeReportRepository(),
            reportFileStore = FakeReportFileStore(),
            reportCsvBuilder = ReportCsvBuilder(),
            syncReportUseCase = noOpSyncReportUseCase(),
        )

        val result = useCase("session-1")

        assertTrue(result.isFailure)
    }
}

private class ReportAuthRepository(private val userId: String?) : AuthRepository {
    override val userIdFlow: Flow<String?> = flowOf(userId)
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
}

private class ReportSessionRepository(private val session: Session?) : SessionRepository {
    override fun observeAllSessions(userId: String): Flow<List<Session>> = flowOf(session?.let(::listOf).orEmpty())
    override suspend fun getSessionById(sessionId: String): Session? = session?.takeIf { it.id == sessionId }
}

private class ReportSampleRepository(
    private val samples: List<Sample>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples)
    override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String): Flow<List<Sample>> =
        flowOf(samples.filter { it.sessionId == sessionId && it.userId == userId })

    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> =
        samples.filter { it.sessionId == sessionId && it.userId == userId }

    override suspend fun getSamplesPendingSync(userId: String): List<Sample> = emptyList()
}

private class ReportDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
    private val eggCounts: List<EggCount>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String): List<EggCount> =
        eggCounts
}

private class FakeReportRepository : ReportRepository {
    var lastInserted: com.agarthavision.domain.model.Report? = null

    override suspend fun insert(report: com.agarthavision.domain.model.Report) {
        lastInserted = report
    }

    override fun observeForSession(
        sessionId: String,
        userId: String,
    ): Flow<List<com.agarthavision.domain.model.Report>> = flowOf(emptyList())

    override suspend fun getById(reportId: String): com.agarthavision.domain.model.Report? = null

    override suspend fun getReportsPendingSync(userId: String): List<com.agarthavision.domain.model.Report> = emptyList()

    override suspend fun updateSupabaseStatus(
        reportId: String,
        status: ReportSyncStatus,
    ) = Unit
}

private class FakeReportFileStore : ReportFileStore {
    var lastReportId: String? = null
    var lastCsv: String = ""

    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String {
        lastReportId = reportId
        lastCsv = csv
        return "/downloads/report.csv"
    }
}

private fun reportSession(sessionId: String, userId: String): Session =
    Session(
        id = sessionId,
        userId = userId,
        deviceId = "device-1",
        startedAt = 1_000L,
        endedAt = 2_000L,
        notes = null,
        label = "Session A",
    )

private fun reportSample(id: String, sessionId: String, userId: String, isRepeat: Boolean): Sample =
    Sample(
        id = id,
        userId = userId,
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = sessionId,
        filePath = "/tmp/$id.jpg",
        storagePath = "$userId/$id.jpg",
        inferenceModelVersion = "model-1",
        isManual = false,
        isRepeat = isRepeat,
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        status = SampleStatus.SYNCED,
    )

private fun reportDetection(sampleId: String, classLabel: String, confidence: Float): Detection =
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

private fun noOpSyncReportUseCase(): SyncReportUseCase =
    SyncReportUseCase(
        reportDao = NoOpReportDao(),
        remoteDataSource = NoOpReportRemoteDataSource(),
    )

private class NoOpReportDao : com.agarthavision.data.local.dao.ReportDao {
    override suspend fun insertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
    override fun observeReportsForSession(
        sessionId: String,
        userId: String,
    ): Flow<List<com.agarthavision.data.local.entity.ReportEntity>> = flowOf(emptyList())
    override suspend fun getReportById(reportId: String): com.agarthavision.data.local.entity.ReportEntity? = null
    override suspend fun getReportsPendingSync(userId: String): List<com.agarthavision.data.local.entity.ReportEntity> = emptyList()
    override suspend fun updateSupabaseStatus(reportId: String, status: String) = Unit
}

private class NoOpReportRemoteDataSource : com.agarthavision.data.supabase.ReportRemoteDataSource(
    supabase = org.mockito.kotlin.mock(),
    gson = com.google.gson.Gson(),
) {
    override suspend fun upsertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
}
