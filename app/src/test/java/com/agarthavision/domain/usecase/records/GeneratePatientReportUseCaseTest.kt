package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.RecordsTotals
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
import com.agarthavision.domain.repository.PatientReportPdfRenderer
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.sync.RecordingSyncScheduler
import com.agarthavision.domain.usecase.patients.PatientSort
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Every fixture below is prefixed `Pr` (Patient Report) — this file shares a package with
// GenerateSessionReportUseCaseTest.kt, whose own private fixtures (FakeReportRepository,
// FakePatientRepository, patient(), reportSession(), etc.) would otherwise collide by name
// with a same-named private top-level declaration here.
@RunWith(RobolectricTestRunner::class)
class GeneratePatientReportUseCaseTest {

    @Test
    fun `defaults to every session and pools findings across them`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val useCase = standardUseCase(reportRepository = reportRepository)

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(ReportType.PATIENT, report.reportType)
        assertNull(report.sessionId)
        assertEquals("patient-1", report.patientId)
        assertEquals(setOf("session-1", "session-2"), report.sessionIds.toSet())
        // 1 sample in session-1 + 1 sample in session-2.
        assertEquals(2, report.totalSamples)
        assertEquals(3, report.totalEggsConfirmed)
        assertEquals(reportRepository.lastInserted?.id, report.id)
    }

    @Test
    fun `a date range scope excludes sessions outside it`() = runTest {
        val useCase = standardUseCase()

        val result = useCase(
            "patient-1",
            PatientReportScope(startDate = LocalDate.of(2026, 1, 2), endDate = LocalDate.of(2026, 1, 2)),
        )

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(listOf("session-2"), report.sessionIds)
        assertEquals(1, report.totalSamples)
    }

    @Test
    fun `a session-id subset only reports the selected sessions`() = runTest {
        val useCase = standardUseCase()

        val result = useCase("patient-1", PatientReportScope(sessionIds = setOf("session-1")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("session-1"), result.getOrThrow().sessionIds)
    }

    @Test
    fun `a foreign session id in the subset fails rather than silently dropping`() = runTest {
        val useCase = standardUseCase()

        val result = useCase("patient-1", PatientReportScope(sessionIds = setOf("session-1", "not-this-patients")))

        assertTrue(result.isFailure)
    }

    @Test
    fun `sessions with zero verified samples are dropped from the report`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val useCase = standardUseCase(
            reportRepository = reportRepository,
            samples = listOf(prSample("sample-1", "session-1")),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        // session-2 had no samples and must be excluded entirely, not reported as a zero row.
        assertEquals(listOf("session-1"), report.sessionIds)
    }

    @Test
    fun `fails with the no-verified-samples message when nothing survives`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val fileStore = PrFakeReportFileStore()
        val useCase = standardUseCase(reportRepository = reportRepository, fileStore = fileStore, samples = emptyList())

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
        assertEquals(
            GeneratePatientReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE,
            result.exceptionOrNull()?.message,
        )
        assertNull(reportRepository.lastInserted)
        assertNull(fileStore.lastPatientPdfReportId)
    }

    @Test
    fun `pools totals across sessions while keeping a per-session breakdown row`() = runTest {
        val renderer = PrFakePdfRenderer()
        val useCase = standardUseCase(renderer = renderer)

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val doc = renderer.lastDocument!!
        assertEquals(2, doc.sessionRows.size)
        assertEquals(2, doc.header.totalSamples)
        assertEquals(3, doc.header.totalEggsConfirmed)
        // Pooled: Ascaris appears in both fields (one per session), 2 in one and 1 in the
        // other — every field has it, so the pooled range is 1..2, not 0..2.
        val ascaris = result.getOrThrow().lpfPerSpecies["Ascaris lumbricoides"]!!
        assertEquals(1, ascaris.min)
        assertEquals(2, ascaris.max)
    }

    @Test
    fun `each generation mints a new report row`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val useCase = standardUseCase(reportRepository = reportRepository)

        val first = useCase("patient-1").getOrThrow()
        val second = useCase("patient-1").getOrThrow()

        assertTrue(first.id != second.id)
    }

    @Test
    fun `a patient with a single session still generates a report`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val sessions = listOf(prSession("session-solo", startedAt = LocalDate.of(2026, 1, 1)))
        val samples = listOf(prSample("sample-solo", "session-solo"))
        val soloFinding = SampleSpeciesFindingEntity("f1", "sample-solo", "Ascaris lumbricoides", null, 2)
        val findingDao = prFindingDao(mapOf("session-solo" to listOf(soloFinding)))
        val useCase = GeneratePatientReportUseCase(
            authRepository = PrFakeAuthRepository("user-1"),
            patientRepository = PrFakePatientRepository(prPatient()),
            psgcRepository = PrFakePsgcRepository(prBarangay()),
            sessionRepository = PrFakeSessionRepository(sessions),
            sampleRepository = PrFakeSampleRepository(samples),
            detectionRepository = PrFakeDetectionRepository(
                eggCountsBySession = mapOf("session-solo" to listOf(EggCount("Ascaris", 2))),
            ),
            findingDao = findingDao,
            reportRepository = reportRepository,
            reportFileStore = PrFakeReportFileStore(),
            patientReportPdfBuilder = PatientReportPdfBuilder(),
            patientReportPdfRenderer = PrFakePdfRenderer(),
            syncReportUseCase = prNoOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(listOf("session-solo"), report.sessionIds)
        assertEquals(1, report.totalSamples)
        assertEquals(2, report.totalEggsConfirmed)
    }

    @Test
    fun `a session belonging to another user is excluded even though it names the same patient`() = runTest {
        // getSessionsForPatient is the FK-safe boundary SessionRepository already enforces
        // (filters on both patientId and userId); this pins that a cross-user session leaking
        // into the candidate list never makes it into a generated report.
        val reportRepository = PrFakeReportRepository()
        val ownSession = prSession("session-1", startedAt = LocalDate.of(2026, 1, 1))
        val otherUsersSession = ownSession.copy(id = "session-other-user", userId = "user-2")
        val useCase = GeneratePatientReportUseCase(
            authRepository = PrFakeAuthRepository("user-1"),
            patientRepository = PrFakePatientRepository(prPatient()),
            psgcRepository = PrFakePsgcRepository(prBarangay()),
            sessionRepository = PrFakeSessionRepository(listOf(ownSession, otherUsersSession)),
            sampleRepository = PrFakeSampleRepository(
                listOf(prSample("sample-1", "session-1"), prSample("sample-other", "session-other-user")),
            ),
            detectionRepository = PrFakeDetectionRepository(eggCountsBySession = emptyMap()),
            findingDao = prFindingDao(mapOf("session-1" to emptyList())),
            reportRepository = reportRepository,
            reportFileStore = PrFakeReportFileStore(),
            patientReportPdfBuilder = PatientReportPdfBuilder(),
            patientReportPdfRenderer = PrFakePdfRenderer(),
            syncReportUseCase = prNoOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        assertEquals(listOf("session-1"), result.getOrThrow().sessionIds)
    }

    @Test
    fun `fails when the patient is not on this device`() = runTest {
        val useCase = standardUseCase(patient = null)

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
        assertEquals(
            GeneratePatientReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `fails when no user is authenticated`() = runTest {
        val useCase = standardUseCase(userId = null)

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `the report row, patient age, and pdf 'generated at' all share one instant`() = runTest {
        val reportRepository = PrFakeReportRepository()
        val renderer = PrFakePdfRenderer()
        val useCase = standardUseCase(reportRepository = reportRepository, renderer = renderer)

        useCase("patient-1")

        val generatedAt = reportRepository.lastInserted!!.generatedAt
        assertEquals(generatedAt, renderer.lastDocument!!.header.generatedAt)
    }

    @Test
    fun `an unknown psgc code prints the raw code as the address`() = runTest {
        val renderer = PrFakePdfRenderer()
        val useCase = standardUseCase(
            renderer = renderer,
            patient = prPatient(psgcBarangayCode = "9999999999"),
            barangay = null,
        )

        useCase("patient-1")

        assertEquals("9999999999", renderer.lastDocument!!.header.address)
    }

    @Suppress("LongParameterList")
    private fun standardUseCase(
        reportRepository: PrFakeReportRepository = PrFakeReportRepository(),
        fileStore: PrFakeReportFileStore = PrFakeReportFileStore(),
        renderer: PatientReportPdfRenderer = PrFakePdfRenderer(),
        patient: Patient? = prPatient(),
        barangay: PsgcBarangay? = prBarangay(),
        userId: String? = "user-1",
        samples: List<Sample>? = null,
    ): GeneratePatientReportUseCase {
        val sessions = listOf(
            prSession("session-1", startedAt = LocalDate.of(2026, 1, 1)),
            prSession("session-2", startedAt = LocalDate.of(2026, 1, 2)),
        )
        val effectiveSamples = samples ?: listOf(
            prSample("sample-1", "session-1"),
            prSample("sample-2", "session-2"),
        )
        val findingsBySession = mapOf(
            "session-1" to listOf(SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2)),
            "session-2" to listOf(SampleSpeciesFindingEntity("f2", "sample-2", "Ascaris lumbricoides", null, 1)),
        )
        return GeneratePatientReportUseCase(
            authRepository = PrFakeAuthRepository(userId),
            patientRepository = PrFakePatientRepository(patient),
            psgcRepository = PrFakePsgcRepository(barangay),
            sessionRepository = PrFakeSessionRepository(sessions),
            sampleRepository = PrFakeSampleRepository(effectiveSamples),
            detectionRepository = PrFakeDetectionRepository(
                eggCountsBySession = mapOf(
                    "session-1" to listOf(EggCount("Ascaris", 2)),
                    "session-2" to listOf(EggCount("Ascaris", 1)),
                ),
            ),
            findingDao = prFindingDao(findingsBySession),
            reportRepository = reportRepository,
            reportFileStore = fileStore,
            patientReportPdfBuilder = PatientReportPdfBuilder(),
            patientReportPdfRenderer = renderer,
            syncReportUseCase = prNoOpSyncReportUseCase(),
            syncScheduler = RecordingSyncScheduler(),
        )
    }

    private fun prFindingDao(bySession: Map<String, List<SampleSpeciesFindingEntity>>): SampleSpeciesFindingDao {
        val dao: SampleSpeciesFindingDao = org.mockito.kotlin.mock()
        kotlinx.coroutines.runBlocking {
            bySession.forEach { (sessionId, findings) ->
                org.mockito.kotlin.whenever(dao.getFindingsForSession(sessionId, "user-1")).thenReturn(findings)
            }
        }
        return dao
    }
}

private fun prSession(id: String, startedAt: LocalDate): Session = Session(
    id = id,
    userId = "user-1",
    deviceId = "device-1",
    startedAt = startedAt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    patientId = "patient-1",
    label = "Session ${id.takeLast(1)}",
)

private fun prSample(id: String, sessionId: String): Sample = Sample(
    id = id,
    userId = "user-1",
    timestamp = 1_000L,
    verifiedAt = 2_000L,
    deviceId = "device-1",
    sessionId = sessionId,
    filePath = "/tmp/$id.jpg",
    storagePath = "user-1/$id.jpg",
    inferenceModelVersion = "model-1",
    isManual = false,
    status = SampleStatus.SYNCED,
)

private fun prPatient(
    id: String = "patient-1",
    psgcBarangayCode: String = "0723017001",
): Patient = Patient(
    id = id,
    lastname = "Cruz",
    firstname = "Gerald",
    sex = Sex.MALE,
    birthdate = LocalDate.of(2000, 1, 1),
    psgcBarangayCode = psgcBarangayCode,
    createdBy = "user-1",
    createdAt = java.time.Instant.ofEpochMilli(0),
    updatedAt = java.time.Instant.ofEpochMilli(0),
)

private fun prBarangay(): PsgcBarangay = PsgcBarangay(
    code = "0723017001",
    name = "Lahug",
    cityMuniName = "City of Cebu",
    provinceName = null,
    regionName = "Region VII (Central Visayas)",
)

private fun prNoOpSyncReportUseCase(): SyncReportUseCase =
    SyncReportUseCase(
        reportDao = PrNoOpReportDao(),
        remoteDataSource = PrNoOpReportRemoteDataSource(),
        reportFileStore = PrFakeReportFileStore(),
    )

private class PrFakeAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeLocalIdentity(): Flow<LocalIdentity?> =
        flowOf(userId?.let { LocalIdentity(userId = it, email = "medtech@example.com", displayName = "Dr. Reyes") })
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) =
        PasswordChangeResult.Failed
}

private class PrFakePatientRepository(private val patient: Patient?) : PatientRepository {
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

private class PrFakePsgcRepository(private val barangay: PsgcBarangay?) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> = emptyList()
    override suspend fun getBarangay(code: String): PsgcBarangay? = barangay
}

private class PrFakeSessionRepository(private val sessions: List<Session>) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(sessions)
    override suspend fun getSessionById(sessionId: String): Session? = sessions.firstOrNull { it.id == sessionId }
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> = flowOf(sessions)
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
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false

    override suspend fun getSessionsForPatient(patientId: String, userId: String): List<Session> =
        sessions.filter { it.patientId == patientId && it.userId == userId }
}

private class PrFakeSampleRepository(private val samples: List<Sample>) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples)
    override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(samples.filter { it.sessionId == sessionId })
    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
        samples.filter { it.sessionId == sessionId }
    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()
    override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(emptyList())
}

private class PrFakeDetectionRepository(
    private val eggCountsBySession: Map<String, List<EggCount>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String) =
        emptyList<com.agarthavision.domain.model.Detection>()
    override fun observeDetectionsForSample(sampleId: String) =
        flowOf(emptyList<com.agarthavision.domain.model.Detection>())
    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?): List<EggCount> =
        eggCountsBySession[sessionId].orEmpty()
    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}

private class PrFakeReportRepository : ReportRepository {
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
    override suspend fun getReportsPendingSync(userId: String): List<com.agarthavision.domain.model.Report> =
        emptyList()
    override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) = Unit
}

private class PrFakeReportFileStore : ReportFileStore {
    var lastPatientPdfReportId: String? = null
    var lastPdfBytes: ByteArray = ByteArray(0)

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String {
        lastPdfBytes = pdf
        return "/Documents/AgarthaVision/report.pdf"
    }

    override suspend fun writePatientPdf(reportId: String, patientId: String, pdf: ByteArray): String {
        lastPatientPdfReportId = reportId
        lastPdfBytes = pdf
        return "/Documents/AgarthaVision/patient-report-$reportId.pdf"
    }

    override suspend fun readBytes(path: String): ByteArray? = lastPdfBytes

    override suspend fun delete(path: String): Boolean = false
}

private class PrFakePdfRenderer : PatientReportPdfRenderer {
    var lastDocument: PatientReportPdfDocument? = null

    override suspend fun render(document: PatientReportPdfDocument): ByteArray {
        lastDocument = document
        return byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())
    }
}

private class PrNoOpReportDao : com.agarthavision.data.local.dao.ReportDao {
    override suspend fun insertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
    override suspend fun updateFilePaths(reportId: String, pdfFilePath: String?, csvFilePath: String?) = Unit
    override suspend fun countReportsForPatient(patientId: String): Int = 0
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
    override suspend fun getReportsPendingSync(userId: String): List<com.agarthavision.data.local.entity.ReportEntity> =
        emptyList()
    override suspend fun deleteReport(reportId: String) = Unit
    override suspend fun updateSupabaseStatus(reportId: String, status: String) = Unit
    override suspend fun claimReportsForSessions(sessionIds: List<String>, userId: String) = Unit
    override fun observePendingCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeFailedCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeUnsyncedCount(userId: String): Flow<Int> = flowOf(0)
}

private class PrNoOpReportRemoteDataSource : com.agarthavision.data.supabase.ReportRemoteDataSource(
    supabaseProvider = org.mockito.kotlin.mock(),
    gson = com.google.gson.Gson(),
) {
    override suspend fun upsertReport(report: com.agarthavision.data.local.entity.ReportEntity) = Unit
}
