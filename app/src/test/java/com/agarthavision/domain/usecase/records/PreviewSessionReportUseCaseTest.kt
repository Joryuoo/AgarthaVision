package com.agarthavision.domain.usecase.records

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportPdfDocument
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
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.patients.PatientSort
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewSessionReportUseCaseTest {

    @Test
    fun `happy path returns rendered pdf bytes and passes report config to renderer`() = runTest {
        val renderer = PsFakeReportPdfRenderer()
        val identity = LocalIdentity(userId = "user-1", email = "medtech@hospital.org", displayName = "Dr. Reyes")
        val useCase = standardUseCase(
            renderer = renderer,
            identity = identity,
        )

        val result = useCase(
            "session-1",
            config = ReportConfig(includeSampleImages = true, patientNameOption = PatientNameOption.SHOW),
        )

        assertTrue(result.isSuccess)
        val bytes = result.getOrThrow()
        assertTrue(PS_FAKE_PDF_BYTES.contentEquals(bytes))

        val header = renderer.lastDocument!!.header
        assertEquals("Cruz, Gerald", header.patientName)
        assertEquals(Sex.MALE, header.patientSex)
        assertEquals("Dr. Reyes", header.generatedByName)
        assertEquals("medtech@hospital.org", header.generatedByEmail)
        assertEquals("hospital.org", header.organizationName)
        assertTrue(header.includeSampleImages)
        assertEquals(2, header.fieldsExamined)
        assertEquals(2, header.totalPages)
        assertEquals(2, renderer.lastDocument!!.appendixSamples.size)
        assertEquals("sample-1", renderer.lastDocument!!.appendixSamples[0].sampleId)
        assertEquals("sample-2", renderer.lastDocument!!.appendixSamples[1].sampleId)
    }

    @Test
    fun `formats patient name per patientNameOption CENSOR and EXCLUDE`() = runTest {
        val renderer = PsFakeReportPdfRenderer()
        val patient = psPatient(lastname = "Cruz", firstname = "Gerald")
        val useCase = standardUseCase(renderer = renderer, patient = patient)

        val resultCensor = useCase("session-1", config = ReportConfig(patientNameOption = PatientNameOption.CENSOR))
        assertTrue(resultCensor.isSuccess)
        assertEquals(patient.maskedDisplayName, renderer.lastDocument!!.header.patientName)

        val resultExclude = useCase("session-1", config = ReportConfig(patientNameOption = PatientNameOption.EXCLUDE))
        assertTrue(resultExclude.isSuccess)
        assertEquals("Excluded", renderer.lastDocument!!.header.patientName)
    }

    @Test
    fun `includeSampleImages false excludes appendix samples and leaves single page`() = runTest {
        val renderer = PsFakeReportPdfRenderer()
        val useCase = standardUseCase(renderer = renderer)

        val result = useCase("session-1", config = ReportConfig(includeSampleImages = false))

        assertTrue(result.isSuccess)
        val header = renderer.lastDocument!!.header
        assertTrue(!header.includeSampleImages)
        assertEquals(1, header.totalPages)
        assertTrue(renderer.lastDocument!!.appendixSamples.isEmpty())
    }

    @Test
    fun `fails when user is not signed in`() = runTest {
        val useCase = standardUseCase(userId = null)

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals("Sign in to generate reports.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when session does not exist`() = runTest {
        val useCase = standardUseCase(session = null)

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals("Session session-1 does not exist.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when session is owned by another user`() = runTest {
        val foreignSession = psSession("session-1", userId = "user-2")
        val useCase = standardUseCase(session = foreignSession, userId = "user-1")

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals("Session session-1 is not owned by the current user.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when session has zero verified samples`() = runTest {
        val useCase = standardUseCase(samples = emptyList())

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals(GenerateSessionReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when patient is not on device`() = runTest {
        val useCase = standardUseCase(patient = null)

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals(GenerateSessionReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Suppress("LongParameterList")
    private fun standardUseCase(
        renderer: PsFakeReportPdfRenderer = PsFakeReportPdfRenderer(),
        patient: Patient? = psPatient(),
        session: Session? = psSession("session-1", "user-1"),
        userId: String? = "user-1",
        identity: LocalIdentity? = LocalIdentity(userId = "user-1", email = "medtech@example.com"),
        samples: List<Sample> = listOf(
            psSample("sample-1", "session-1", "user-1"),
            psSample("sample-2", "session-1", "user-1"),
        ),
    ): PreviewSessionReportUseCase {
        val findings = listOf(
            SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2),
            SampleSpeciesFindingEntity("f2", "sample-1", "Trichuris trichiura", null, 1),
        )
        return PreviewSessionReportUseCase(
            authRepository = PsReportAuthRepository(userId = userId, identity = identity),
            sessionRepository = PsReportSessionRepository(session = session),
            sampleRepository = PsReportSampleRepository(samples = samples),
            detectionRepository = PsReportDetectionRepository(
                detectionsBySample = mapOf(
                    "sample-1" to listOf(psReportDetection("sample-1", "Ascaris", 0.91f)),
                ),
                eggCounts = listOf(
                    EggCount("Ascaris", 2),
                    EggCount("Trichuris trichiura", 1),
                ),
            ),
            findingDao = mockFindingDao(findings),
            patientRepository = PsFakePatientRepository(patient = patient),
            psgcRepository = PsFakePsgcRepository(barangay = psBarangay()),
            reportPdfBuilder = ReportPdfBuilder(),
            reportPdfRenderer = renderer,
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

private fun psIsVisible(rowUserId: String?, callerId: String?) =
    rowUserId == null || rowUserId == callerId

private val PS_FAKE_PDF_BYTES = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

private class PsFakeReportPdfRenderer : ReportPdfRenderer {
    var lastDocument: ReportPdfDocument? = null

    override suspend fun render(document: ReportPdfDocument): ByteArray {
        lastDocument = document
        return PS_FAKE_PDF_BYTES
    }
}

private class PsReportAuthRepository(
    private val userId: String?,
    private val identity: LocalIdentity? = userId?.let { LocalIdentity(userId = it, email = "user@example.com") },
) : AuthRepository {
    override fun observeLocalIdentity(): Flow<LocalIdentity?> = flowOf(identity)
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String, humanCheckToken: String?) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String, humanCheckToken: String?) =
        PasswordChangeResult.Failed
}

private class PsReportSessionRepository(private val session: Session?) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(
        session?.let(::listOf).orEmpty().filter { psIsVisible(it.userId, userId) },
    )
    override suspend fun getSessionById(sessionId: String): Session? = session?.takeIf { it.id == sessionId }
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

private class PsReportSampleRepository(
    private val samples: List<Sample>,
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(samples)
    override suspend fun getSampleById(sampleId: String): Sample? = samples.firstOrNull { it.id == sampleId }
    override fun observeSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(samples.filter { s -> s.sessionId == sessionId && psIsVisible(s.userId, userId) })

    override suspend fun getSamplesForSession(sessionId: String, userId: String?): List<Sample> =
        samples.filter { s -> s.sessionId == sessionId && psIsVisible(s.userId, userId) }

    override suspend fun getSamplesPendingSyncIncludingDeleted(userId: String): List<Sample> = emptyList()

    override fun observeFlaggedSamplesForSession(sessionId: String, userId: String?): Flow<List<Sample>> =
        flowOf(emptyList())
}

private class PsReportDetectionRepository(
    private val detectionsBySample: Map<String, List<Detection>>,
    private val eggCounts: List<EggCount>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String): List<Detection> =
        detectionsBySample[sampleId].orEmpty()

    override fun observeDetectionsForSample(sampleId: String): Flow<List<Detection>> =
        flowOf(detectionsBySample[sampleId].orEmpty())

    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?): List<EggCount> =
        eggCounts

    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}

private class PsFakePatientRepository(private val patient: Patient?) : PatientRepository {
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

private class PsFakePsgcRepository(private val barangay: PsgcBarangay?) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> = emptyList()
    override suspend fun getBarangay(code: String): PsgcBarangay? = barangay
}

@Suppress("LongParameterList")
private fun psPatient(
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

private fun psBarangay(): PsgcBarangay = PsgcBarangay(
    code = "0723017001",
    name = "Lahug",
    cityMuniName = "City of Cebu",
    provinceName = null,
    regionName = "Region VII (Central Visayas)",
)

private fun psSession(sessionId: String, userId: String): Session =
    Session(
        id = sessionId,
        userId = userId,
        deviceId = "device-1",
        startedAt = 1_000L,
        patientId = "patient-1",
        label = "Session A",
    )

private fun psSample(id: String, sessionId: String, userId: String): Sample =
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

private fun psReportDetection(
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
