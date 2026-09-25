package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.ReportFormat
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.agarthavision.domain.sync.RecordingSyncScheduler

class GenerateSessionReportUseCaseTest {
    @Test
    fun `generates a csv report, writes only the csv, and persists metadata`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val useCase = standardUseCase(reportRepository, reportFileStore)

        val result = useCase("session-1", ReportFormat.CSV)

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(report.id, reportFileStore.lastReportId)
        assertEquals(report.id, reportRepository.lastInserted?.id)
        assertEquals("session-1", report.sessionId)
        assertEquals("user-1", report.userId)
        assertEquals(ReportType.SESSION, report.reportType)
        // Both samples count. This asserted 1 while is_repeat existed, because the second
        // was marked a duplicate and excluded. Duplicates are deleted now, so a sample that
        // is still here is a sample that counts.
        assertEquals(2, report.totalSamples)
        assertEquals(3, report.totalEggsConfirmed)
        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura"), report.positiveSpecies)
        // The range, not a mean, and the report path now computes it through the same
        // aggregation the screen uses rather than its own copy of the arithmetic.
        val ascaris = report.lpfPerSpecies["Ascaris lumbricoides"]!!
        assertEquals(0, ascaris.min)
        assertEquals(2, ascaris.max)
        val trichuris = report.lpfPerSpecies["Trichuris trichiura"]!!
        assertEquals(0, trichuris.min)
        assertEquals(1, trichuris.max)
        assertEquals("/Documents/AgarthaVision/report.csv", report.csvFilePath)
        // CSV-format report carries no PDF, and the PDF renderer was never invoked.
        assertNull(report.pdfFilePath)
        assertNull(reportFileStore.lastPdfReportId)
        assertEquals(ReportSyncStatus.PENDING, report.supabaseStatus)
        assertNotNull(report.generatedAt)
        assertTrue(reportFileStore.lastCsv.contains("# report_id: ${report.id}"))
    }

    @Test
    fun `generates a pdf report and writes only the pdf`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val useCase = standardUseCase(reportRepository, reportFileStore)

        val result = useCase("session-1", ReportFormat.PDF)

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals("/Documents/AgarthaVision/report.pdf", report.pdfFilePath)
        // PDF-format report carries no CSV, and the CSV builder never wrote anything.
        assertNull(report.csvFilePath)
        assertEquals("", reportFileStore.lastCsv)
        assertEquals(report.id, reportFileStore.lastPdfReportId)
        assertTrue(FAKE_PDF_BYTES.contentEquals(reportFileStore.lastPdfBytes))
    }

    @Test
    fun `carries the verified species into the generated csv without re-entry`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val findingDao: SampleSpeciesFindingDao = org.mockito.kotlin.mock()
        val findings = listOf(
            SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2)
        )
        org.mockito.kotlin.whenever(findingDao.getFindingsForSession("session-1", "user-1"))
            .thenReturn(findings)

        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1"),
            sessionRepository = ReportSessionRepository(session = reportSession("session-1", "user-1")),
            sampleRepository = ReportSampleRepository(
                samples = listOf(
                    reportSample(id = "sample-1", sessionId = "session-1", userId = "user-1"),
                ),
            ),
            detectionRepository = ReportDetectionRepository(
                detectionsBySample = mapOf(
                    "sample-1" to listOf(
                        reportDetection(
                            sampleId = "sample-1",
                            classLabel = "Ascaris",
                            confidence = 0.91f,
                            expertClass = "Ascaris lumbricoides",
                        ),
                    ),
                ),
                eggCounts = listOf(EggCount("Ascaris lumbricoides", 2)),
            ),
            findingDao = findingDao,
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportCsvBuilder = ReportCsvBuilder(),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )

        val result = useCase("session-1", ReportFormat.CSV)

        assertTrue(result.isSuccess)
        val dataRow = reportFileStore.lastCsv
            .lines()
            .firstOrNull { it.startsWith("sample-1,") }
        assertNotNull(dataRow)
        val fields = dataRow!!.split(",")
        assertEquals("Ascaris lumbricoides", fields[5])
    }

    @Test
    fun `fails when no user is authenticated`() = runTest {
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = null),
            sessionRepository = ReportSessionRepository(session = null),
            sampleRepository = ReportSampleRepository(samples = emptyList()),
            detectionRepository = ReportDetectionRepository(detectionsBySample = emptyMap(), eggCounts = emptyList()),
            findingDao = org.mockito.kotlin.mock(),
            reportRepository = FakeReportRepository(),
            reportFileStore = FakeReportFileStore(),
            reportCsvBuilder = ReportCsvBuilder(),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )

        val result = useCase("session-1", ReportFormat.PDF)

        assertTrue(result.isFailure)
    }

    @Test
    fun `refuses a session with no verified samples and writes nothing`() = runTest {
        // 86d4bzm9k. getSamplesForSession leaves out frames still in the queue, so an empty
        // list is a session whose every sample is unverified, or one with none at all.
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val syncScheduler = RecordingSyncScheduler()
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1"),
            sessionRepository = ReportSessionRepository(session = reportSession("session-1", "user-1")),
            sampleRepository = ReportSampleRepository(samples = emptyList()),
            detectionRepository = ReportDetectionRepository(detectionsBySample = emptyMap(), eggCounts = emptyList()),
            findingDao = org.mockito.kotlin.mock(),
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportCsvBuilder = ReportCsvBuilder(),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = syncScheduler,
        )

        val result = useCase("session-1", ReportFormat.PDF)

        assertTrue(result.isFailure)
        assertEquals(
            GenerateSessionReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE,
            result.exceptionOrNull()?.message,
        )
        assertNull(reportRepository.lastInserted)
        assertNull(reportFileStore.lastPdfReportId)
        assertNull(reportFileStore.lastReportId)
        assertEquals(0, syncScheduler.requests)
    }

    private fun standardUseCase(
        reportRepository: FakeReportRepository,
        reportFileStore: FakeReportFileStore,
    ): GenerateSessionReportUseCase {
        val findingDao: SampleSpeciesFindingDao = org.mockito.kotlin.mock()
        // Default mock behavior for 2 samples, one with Ascaris(2) + Trichuris(1), one clean.
        // Mean Ascaris = (2+0)/2 = 1.0, Mean Trichuris = (1+0)/2 = 0.5
        val findings = listOf(
            SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2),
            SampleSpeciesFindingEntity("f2", "sample-1", "Trichuris trichiura", null, 1),
        )
        kotlinx.coroutines.runBlocking {
            org.mockito.kotlin.whenever(findingDao.getFindingsForSession("session-1", "user-1"))
                .thenReturn(findings)
        }
        return GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1"),
            sessionRepository = ReportSessionRepository(session = reportSession("session-1", "user-1")),
            sampleRepository = ReportSampleRepository(
                samples = listOf(
                    reportSample(id = "sample-1", sessionId = "session-1", userId = "user-1"),
                    reportSample(id = "sample-2", sessionId = "session-1", userId = "user-1"),
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
            findingDao = findingDao,
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportCsvBuilder = ReportCsvBuilder(),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )
    }
}

/** null caller = sees everything; concrete caller = sees own rows plus unowned rows. */
private fun isVisible(rowUserId: String?, callerId: String?) =
    rowUserId == null || rowUserId == callerId

/** Non-empty marker bytes so tests can assert the renderer's output actually reached the file store. */
private val FAKE_PDF_BYTES = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

private class FakeReportPdfRenderer : ReportPdfRenderer {
    override suspend fun render(document: ReportPdfDocument): ByteArray = FAKE_PDF_BYTES
}

private class ReportAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeLocalIdentity(): Flow<com.agarthavision.domain.model.LocalIdentity?> =
        flowOf(userId?.let { com.agarthavision.domain.model.LocalIdentity(userId = it, email = "user@example.com") })
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
}

private class ReportSessionRepository(private val session: Session?) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(
        session?.let(::listOf).orEmpty().filter { isVisible(it.userId, userId) },
    )
    override suspend fun getSessionById(sessionId: String): Session? = session?.takeIf { it.id == sessionId }
    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        flowOf(emptyList())

    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> =
        flowOf(session?.let(::listOf).orEmpty())
    override fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())
    override fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> = flowOf(RecordsTotals())

    override fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = flowOf(emptyList())

    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = flowOf(SessionsCounts())
}

private class ReportSampleRepository(
    private val samples: List<Sample>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples)
    override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(samples.filter { s -> s.sessionId == sessionId && isVisible(s.userId, userId) })

    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
        samples.filter { s -> s.sessionId == sessionId && isVisible(s.userId, userId) }

    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()

    override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(emptyList())
}

private class ReportDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
    private val eggCounts: List<EggCount>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?): List<EggCount> =
        eggCounts

    override fun observeConfirmedEggCountsSince(userId: String, sinceTimestamp: Long): Flow<List<EggCount>> =
        flowOf(emptyList())


    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}

private class FakeReportRepository : ReportRepository {
    var lastInserted: com.agarthavision.domain.model.Report? = null

    override suspend fun insert(report: com.agarthavision.domain.model.Report) {
        lastInserted = report
    }

    override fun observeForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.domain.model.Report>> = flowOf(emptyList())

    override fun observeCountForSession(sessionId: String, userId: String): Flow<Int> = flowOf(0)

    override fun observeAll(
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.domain.model.Report>> = flowOf(emptyList())

    override fun observeAllCount(userId: String): Flow<Int> = flowOf(0)

    override fun observeFiltered(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.domain.model.Report>> = flowOf(emptyList())

    override fun observeFilteredCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> = flowOf(0)

    override suspend fun getById(reportId: String): com.agarthavision.domain.model.Report? = null

    override suspend fun getReportsPendingSync(
        userId: String,
    ): List<com.agarthavision.domain.model.Report> = emptyList()

    override suspend fun updateSupabaseStatus(
        reportId: String,
        status: ReportSyncStatus,
    ) = Unit
}

private class FakeReportFileStore : ReportFileStore {
    var lastReportId: String? = null
    var lastCsv: String = ""
    var lastPdfReportId: String? = null
    var lastPdfBytes: ByteArray = ByteArray(0)

    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String {
        lastReportId = reportId
        lastCsv = csv
        return "/Documents/AgarthaVision/report.csv"
    }

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String {
        lastPdfReportId = reportId
        lastPdfBytes = pdf
        return "/Documents/AgarthaVision/report.pdf"
    }

    override suspend fun readBytes(path: String): ByteArray? = when (path) {
        "/Documents/AgarthaVision/report.pdf" -> lastPdfBytes
        "/Documents/AgarthaVision/report.csv" -> lastCsv.toByteArray()
        else -> null
    }
}

private fun reportSession(sessionId: String, userId: String): Session =
    Session(
        id = sessionId,
        userId = userId,
        deviceId = "device-1",
        startedAt = 1_000L,
        patientId = "patient-1",
        label = "Session A",
    )

private fun reportSample(id: String, sessionId: String, userId: String): Sample =
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
        status = SampleStatus.SYNCED,
    )

private fun reportDetection(
    sampleId: String,
    classLabel: String,
    confidence: Float,
    expertClass: String? = null,
): Detection =
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
        expertClass = expertClass,
    )

private fun noOpSyncReportUseCase(): SyncReportUseCase =
    SyncReportUseCase(
        reportDao = NoOpReportDao(),
        remoteDataSource = NoOpReportRemoteDataSource(),
        reportFileStore = FakeReportFileStore(),
    )

private class NoOpReportDao : com.agarthavision.data.local.dao.ReportDao {
    override suspend fun insertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
    override suspend fun updateFilePaths(
        reportId: String,
        pdfFilePath: String?,
        csvFilePath: String?,
    ) = Unit
    override fun observeReportsForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.data.local.entity.ReportEntity>> = flowOf(emptyList())
    override fun observeReportCountForSession(sessionId: String, userId: String): Flow<Int> = flowOf(0)
    override fun observeAllReports(
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.data.local.entity.ReportEntity>> = flowOf(emptyList())
    override fun observeAllReportsCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeFilteredReports(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.data.local.dao.ReportWithSessionLabel>> = flowOf(emptyList())
    override fun observeFilteredReportsCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> = flowOf(0)
    override suspend fun getReportById(reportId: String): com.agarthavision.data.local.entity.ReportEntity? = null
    override suspend fun getReportsPendingSync(
        userId: String,
    ): List<com.agarthavision.data.local.entity.ReportEntity> = emptyList()
    override suspend fun deleteReport(reportId: String) = Unit
    override suspend fun updateSupabaseStatus(reportId: String, status: String) = Unit
    override suspend fun claimReportsForSessions(sessionIds: List<String>, userId: String) = Unit
    override fun observePendingCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeFailedCount(userId: String): Flow<Int> = flowOf(0)
}

private class NoOpReportRemoteDataSource : com.agarthavision.data.supabase.ReportRemoteDataSource(
    supabaseProvider = org.mockito.kotlin.mock(),
    gson = com.google.gson.Gson(),
) {
    override suspend fun upsertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
}
