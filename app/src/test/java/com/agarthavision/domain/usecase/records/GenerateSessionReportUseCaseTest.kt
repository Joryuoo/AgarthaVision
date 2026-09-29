package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.sync.RecordingSyncScheduler
import com.agarthavision.domain.usecase.patients.PatientSort
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenerateSessionReportUseCaseTest {
    @Test
    fun `generates a pdf report and persists metadata`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val useCase = standardUseCase(reportRepository, reportFileStore)

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(report.id, reportFileStore.lastPdfReportId)
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
        assertEquals("/Documents/AgarthaVision/report.pdf", report.pdfFilePath)
        // PDF-only: no report carries a CSV any more.
        assertNull(report.csvFilePath)
        assertEquals(ReportSyncStatus.PENDING, report.supabaseStatus)
        assertNotNull(report.generatedAt)
        assertTrue(FAKE_PDF_BYTES.contentEquals(reportFileStore.lastPdfBytes))
    }

    @Test
    fun `carries patient age, sex, barangay label and medtech name into the pdf header`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val renderer = FakeReportPdfRenderer()
        val patient = patient(
            lastname = "Cruz",
            firstname = "Gerald",
            sex = Sex.MALE,
            birthdate = LocalDate.of(2000, 1, 1),
            psgcBarangayCode = "0723017001",
        )
        val useCase = standardUseCase(
            reportRepository,
            reportFileStore,
            renderer = renderer,
            patient = patient,
            barangay = barangay(),
            identity = LocalIdentity(userId = "user-1", email = "medtech@example.com", displayName = "Dr. Reyes"),
        )

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        val header = renderer.lastDocument!!.header
        assertEquals(patient.displayName, header.patientName)
        assertEquals(Sex.MALE, header.patientSex)
        assertEquals(patient.ageYears(header.generatedAt), header.patientAgeYears)
        assertEquals("Lahug · City of Cebu · Region VII (Central Visayas)", header.barangayLabel)
        assertEquals("Dr. Reyes", header.generatedByName)
        // The same instant used for the report row's timestamp is the one age was computed
        // from — not a beat apart, which would let the printed age and "Generated at" disagree.
        assertEquals(reportRepository.lastInserted?.generatedAt, header.generatedAt)
    }

    @Test
    fun `falls back to email then user id when display name is blank`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val renderer = FakeReportPdfRenderer()
        val useCaseBlankName = standardUseCase(
            reportRepository,
            reportFileStore,
            renderer = renderer,
            identity = LocalIdentity(userId = "user-1", email = "medtech@example.com", displayName = " "),
        )

        useCaseBlankName("session-1")
        assertEquals("medtech@example.com", renderer.lastDocument!!.header.generatedByName)

        val useCaseNoIdentity = standardUseCase(
            reportRepository,
            reportFileStore,
            renderer = renderer,
            identity = null,
        )
        useCaseNoIdentity("session-1")
        assertEquals("user-1", renderer.lastDocument!!.header.generatedByName)
    }

    @Test
    fun `an unknown psgc code prints the raw code`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val renderer = FakeReportPdfRenderer()
        val patient = patient(psgcBarangayCode = "9999999999")
        val useCase = standardUseCase(
            reportRepository,
            reportFileStore,
            renderer = renderer,
            patient = patient,
            barangay = null,
        )

        useCase("session-1")

        assertEquals("9999999999", renderer.lastDocument!!.header.barangayLabel)
    }

    @Test
    fun `a null sex prints without failing`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val renderer = FakeReportPdfRenderer()
        val patient = patient(sex = null)
        val useCase = standardUseCase(reportRepository, reportFileStore, renderer = renderer, patient = patient)

        useCase("session-1")

        assertNull(renderer.lastDocument!!.header.patientSex)
    }

    @Test
    fun `a codenamed patient prints the codename`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val renderer = FakeReportPdfRenderer()
        val patient = patient(lastname = "ALPHA-M24", firstname = "")
        val useCase = standardUseCase(reportRepository, reportFileStore, renderer = renderer, patient = patient)

        useCase("session-1")

        assertEquals("ALPHA-M24", renderer.lastDocument!!.header.patientName)
    }

    @Test
    fun `fails and writes nothing when the session's patient is not on this device`() = runTest {
        val reportRepository = FakeReportRepository()
        val reportFileStore = FakeReportFileStore()
        val syncScheduler = RecordingSyncScheduler()
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1"),
            sessionRepository = ReportSessionRepository(session = reportSession("session-1", "user-1")),
            sampleRepository = ReportSampleRepository(
                samples = listOf(reportSample(id = "sample-1", sessionId = "session-1", userId = "user-1")),
            ),
            detectionRepository = ReportDetectionRepository(
                detectionsBySample = emptyMap(),
                eggCounts = emptyList(),
            ),
            findingDao = mockFindingDao(emptyList()),
            patientRepository = FakePatientRepository(patient = null),
            psgcRepository = FakePsgcRepository(barangay = null),
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = syncScheduler,
        )

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals(
            GenerateSessionReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE,
            result.exceptionOrNull()?.message,
        )
        assertNull(reportRepository.lastInserted)
        assertNull(reportFileStore.lastPdfReportId)
        assertEquals(0, syncScheduler.requests)
    }

    @Test
    fun `fails when no user is authenticated`() = runTest {
        val useCase = GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = null),
            sessionRepository = ReportSessionRepository(session = null),
            sampleRepository = ReportSampleRepository(samples = emptyList()),
            detectionRepository = ReportDetectionRepository(detectionsBySample = emptyMap(), eggCounts = emptyList()),
            findingDao = mockFindingDao(emptyList()),
            patientRepository = FakePatientRepository(patient = patient()),
            psgcRepository = FakePsgcRepository(barangay = barangay()),
            reportRepository = FakeReportRepository(),
            reportFileStore = FakeReportFileStore(),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )

        val result = useCase("session-1")

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
            findingDao = mockFindingDao(emptyList()),
            patientRepository = FakePatientRepository(patient = patient()),
            psgcRepository = FakePsgcRepository(barangay = barangay()),
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = FakeReportPdfRenderer(),
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = syncScheduler,
        )

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals(
            GenerateSessionReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE,
            result.exceptionOrNull()?.message,
        )
        assertNull(reportRepository.lastInserted)
        assertNull(reportFileStore.lastPdfReportId)
        assertEquals(0, syncScheduler.requests)
    }

    @Suppress("LongParameterList")
    private fun standardUseCase(
        reportRepository: FakeReportRepository,
        reportFileStore: FakeReportFileStore,
        renderer: ReportPdfRenderer = FakeReportPdfRenderer(),
        patient: Patient = patient(),
        barangay: PsgcBarangay? = barangay(),
        identity: LocalIdentity? = LocalIdentity(userId = "user-1", email = "medtech@example.com"),
    ): GenerateSessionReportUseCase {
        val findings = listOf(
            SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2),
            SampleSpeciesFindingEntity("f2", "sample-1", "Trichuris trichiura", null, 1),
        )
        return GenerateSessionReportUseCase(
            authRepository = ReportAuthRepository(userId = "user-1", identity = identity),
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
            findingDao = mockFindingDao(findings),
            patientRepository = FakePatientRepository(patient = patient),
            psgcRepository = FakePsgcRepository(barangay = barangay),
            reportRepository = reportRepository,
            reportFileStore = reportFileStore,
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = renderer,
            syncReportUseCase = noOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )
    }

    private fun mockFindingDao(findings: List<SampleSpeciesFindingEntity>): SampleSpeciesFindingDao {
        val dao: SampleSpeciesFindingDao = org.mockito.kotlin.mock()
        kotlinx.coroutines.runBlocking {
            org.mockito.kotlin.whenever(dao.getFindingsForSession("session-1", "user-1"))
                .thenReturn(findings)
        }
        return dao
    }
}

/** null caller = sees everything; concrete caller = sees own rows plus unowned rows. */
private fun isVisible(rowUserId: String?, callerId: String?) =
    rowUserId == null || rowUserId == callerId

/** Non-empty marker bytes so tests can assert the renderer's output actually reached the file store. */
private val FAKE_PDF_BYTES = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

private class FakeReportPdfRenderer : ReportPdfRenderer {
    var lastDocument: ReportPdfDocument? = null

    override suspend fun render(document: ReportPdfDocument): ByteArray {
        lastDocument = document
        return FAKE_PDF_BYTES
    }
}

private class ReportAuthRepository(
    private val userId: String?,
    private val identity: LocalIdentity? = userId?.let { LocalIdentity(userId = it, email = "user@example.com") },
) : AuthRepository {
    override fun observeLocalIdentity(): Flow<LocalIdentity?> = flowOf(identity)
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
    var lastPdfReportId: String? = null
    var lastPdfBytes: ByteArray = ByteArray(0)

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String {
        lastPdfReportId = reportId
        lastPdfBytes = pdf
        return "/Documents/AgarthaVision/report.pdf"
    }

    override suspend fun readBytes(path: String): ByteArray? = when (path) {
        "/Documents/AgarthaVision/report.pdf" -> lastPdfBytes
        else -> null
    }
}

private class FakePatientRepository(private val patient: Patient?) : PatientRepository {
    override fun observePatients(
        userId: String,
        query: String,
        limit: Int,
        sort: PatientSort,
        sex: Sex?,
        barangayCode: String?,
        minBirthdate: Long?,
        maxBirthdate: Long?,
        todayStartMillis: Long?,
        sevenDaysAgoMillis: Long?,
    ): Flow<List<Patient>> = flowOf(patient?.let(::listOf).orEmpty())

    override fun observePatientCount(
        userId: String,
        query: String,
        sex: Sex?,
        barangayCode: String?,
        minBirthdate: Long?,
        maxBirthdate: Long?,
        sort: PatientSort,
        todayStartMillis: Long?,
        sevenDaysAgoMillis: Long?,
    ): Flow<Int> = flowOf(if (patient != null) 1 else 0)

    override suspend fun getPatientById(patientId: String): Patient? = patient?.takeIf { it.id == patientId }

    override fun observePatientById(patientId: String): Flow<Patient?> = flowOf(patient)

    override suspend fun insert(patient: Patient) = Unit

    override suspend fun update(patient: Patient) = Unit

    override suspend fun findDuplicates(
        userId: String,
        lastname: String,
        firstname: String,
        middleName: String?,
        birthdate: LocalDate,
        sex: Sex,
        excludingId: String,
    ): List<Patient> = emptyList()

    override suspend fun getExistingCodenamesByPrefix(userId: String, prefix: String): List<String> = emptyList()

    override fun observeAddedActivity(userId: String, limit: Int): Flow<List<ActivityItem.PatientAdded>> =
        flowOf(emptyList())
}

private class FakePsgcRepository(private val barangay: PsgcBarangay?) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> = emptyList()
    override suspend fun getBarangay(code: String): PsgcBarangay? = barangay
}

@Suppress("LongParameterList") // Every parameter is an independent test fixture field; a
// wrapper object would add ceremony for a private test helper with a handful of call sites.
private fun patient(
    id: String = "patient-1",
    lastname: String = "Cruz",
    firstname: String = "Gerald",
    sex: Sex? = Sex.MALE,
    birthdate: LocalDate = LocalDate.of(2000, 1, 1),
    psgcBarangayCode: String = "0723017001",
): Patient = Patient(
    id = id,
    lastname = lastname,
    firstname = firstname,
    sex = sex,
    birthdate = birthdate,
    psgcBarangayCode = psgcBarangayCode,
    createdBy = "user-1",
    createdAt = Instant.ofEpochMilli(0),
    updatedAt = Instant.ofEpochMilli(0),
)

private fun barangay(): PsgcBarangay = PsgcBarangay(
    code = "0723017001",
    name = "Lahug",
    cityMuniName = "City of Cebu",
    provinceName = null,
    regionName = "Region VII (Central Visayas)",
)

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
    override fun observeUnsyncedCount(userId: String): Flow<Int> = flowOf(0)
}

private class NoOpReportRemoteDataSource : com.agarthavision.data.supabase.ReportRemoteDataSource(
    supabaseProvider = org.mockito.kotlin.mock(),
    gson = com.google.gson.Gson(),
) {
    override suspend fun upsertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
}
