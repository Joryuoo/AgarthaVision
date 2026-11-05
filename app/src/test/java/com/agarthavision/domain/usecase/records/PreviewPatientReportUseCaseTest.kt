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
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.ReportConfig
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
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.patients.PatientSort
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewPatientReportUseCaseTest {

    @Test
    fun `happy path returns rendered pdf bytes across scoped sessions and passes config and organization`() = runTest {
        val renderer = PpFakePdfRenderer()
        val identity = LocalIdentity(userId = "user-1", email = "doctor@clinic.ph", displayName = "Dr. Cruz")
        val useCase = standardUseCase(renderer = renderer, identity = identity)

        val result = useCase(
            "patient-1",
            config = ReportConfig(includeSampleImages = true, patientNameOption = PatientNameOption.SHOW),
        )

        assertTrue(result.isSuccess)
        val bytes = result.getOrThrow()
        assertTrue(PP_FAKE_PDF_BYTES.contentEquals(bytes))

        val header = renderer.lastDocument!!.header
        assertEquals("Cruz, Gerald", header.patientDisplayName)
        assertEquals(2, header.totalSessions)
        assertEquals(2, header.totalSamples)
        assertEquals(3, header.totalEggsConfirmed)
        assertEquals("Dr. Cruz", header.generatedBy)
        assertEquals("doctor@clinic.ph", header.generatedByEmail)
        assertEquals("clinic.ph", header.organizationName)
        assertTrue(header.includeSampleImages)
        assertEquals(2, header.totalPages)
        assertEquals(2, renderer.lastDocument!!.appendixSamples.size)
        assertEquals("Session 1", renderer.lastDocument!!.appendixSamples[0].sessionLabel)
        assertEquals("Session 2", renderer.lastDocument!!.appendixSamples[1].sessionLabel)
    }

    @Test
    fun `formats patient name per patientNameOption CENSOR and EXCLUDE`() = runTest {
        val renderer = PpFakePdfRenderer()
        val patient = ppPatient()
        val useCase = standardUseCase(renderer = renderer, patient = patient)

        val resultCensor = useCase("patient-1", config = ReportConfig(patientNameOption = PatientNameOption.CENSOR))
        assertTrue(resultCensor.isSuccess)
        assertEquals(patient.maskedDisplayName, renderer.lastDocument!!.header.patientDisplayName)

        val resultExclude = useCase("patient-1", config = ReportConfig(patientNameOption = PatientNameOption.EXCLUDE))
        assertTrue(resultExclude.isSuccess)
        assertEquals("Excluded", renderer.lastDocument!!.header.patientDisplayName)
    }

    @Test
    fun `includeSampleImages false excludes appendix samples and leaves single page`() = runTest {
        val renderer = PpFakePdfRenderer()
        val useCase = standardUseCase(renderer = renderer)

        val result = useCase("patient-1", config = ReportConfig(includeSampleImages = false))

        assertTrue(result.isSuccess)
        val header = renderer.lastDocument!!.header
        assertTrue(!header.includeSampleImages)
        assertEquals(1, header.totalPages)
        assertTrue(renderer.lastDocument!!.appendixSamples.isEmpty())
    }

    @Test
    fun `date range scope filters sessions in preview`() = runTest {
        val renderer = PpFakePdfRenderer()
        val useCase = standardUseCase(renderer = renderer)

        val result = useCase(
            "patient-1",
            scope = PatientReportScope(startDate = LocalDate.of(2026, 1, 2), endDate = LocalDate.of(2026, 1, 2)),
        )

        assertTrue(result.isSuccess)
        val doc = renderer.lastDocument!!
        assertEquals(1, doc.sessionRows.size)
        assertEquals("session-2", doc.sessionRows[0].sessionId)
        assertEquals(1, doc.header.totalSamples)
    }

    @Test
    fun `session ids scope filters sessions in preview`() = runTest {
        val renderer = PpFakePdfRenderer()
        val useCase = standardUseCase(renderer = renderer)

        val result = useCase(
            "patient-1",
            scope = PatientReportScope(sessionIds = setOf("session-1")),
        )

        assertTrue(result.isSuccess)
        val doc = renderer.lastDocument!!
        assertEquals(1, doc.sessionRows.size)
        assertEquals("session-1", doc.sessionRows[0].sessionId)
    }

    @Test
    fun `sessions with zero verified samples are dropped from preview`() = runTest {
        val renderer = PpFakePdfRenderer()
        val useCase = standardUseCase(
            renderer = renderer,
            samples = listOf(ppSample("sample-1", "session-1")),
        )

        val result = useCase("patient-1")

        assertTrue(result.isSuccess)
        val doc = renderer.lastDocument!!
        assertEquals(1, doc.sessionRows.size)
        assertEquals("session-1", doc.sessionRows[0].sessionId)
    }

    @Test
    fun `fails when user is not signed in`() = runTest {
        val useCase = standardUseCase(userId = null)

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
        assertEquals("Sign in to generate reports.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when patient is not on device`() = runTest {
        val useCase = standardUseCase(patient = null)

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
        assertEquals(GeneratePatientReportUseCase.PATIENT_NOT_ON_DEVICE_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `fails when zero eligible verified samples in scope`() = runTest {
        val useCase = standardUseCase(samples = emptyList())

        val result = useCase("patient-1")

        assertTrue(result.isFailure)
        assertEquals(GeneratePatientReportUseCase.NO_VERIFIED_SAMPLES_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Suppress("LongParameterList")
    private fun standardUseCase(
        renderer: PatientReportPdfRenderer = PpFakePdfRenderer(),
        patient: Patient? = ppPatient(),
        userId: String? = "user-1",
        identity: LocalIdentity? = LocalIdentity(
            userId = "user-1",
            email = "medtech@example.com",
            displayName = "Dr. Reyes",
        ),
        samples: List<Sample>? = null,
        sessions: List<Session> = listOf(
            ppSession("session-1", startedAt = LocalDate.of(2026, 1, 1)),
            ppSession("session-2", startedAt = LocalDate.of(2026, 1, 2)),
        ),
    ): PreviewPatientReportUseCase {
        val effectiveSamples = samples ?: listOf(
            ppSample("sample-1", "session-1"),
            ppSample("sample-2", "session-2"),
        )
        val findingsBySession = mapOf(
            "session-1" to listOf(SampleSpeciesFindingEntity("f1", "sample-1", "Ascaris lumbricoides", null, 2)),
            "session-2" to listOf(SampleSpeciesFindingEntity("f2", "sample-2", "Ascaris lumbricoides", null, 1)),
        )
        return PreviewPatientReportUseCase(
            authRepository = PpFakeAuthRepository(userId, identity),
            patientRepository = PpFakePatientRepository(patient),
            psgcRepository = PpFakePsgcRepository(ppBarangay()),
            sessionRepository = PpFakeSessionRepository(sessions),
            sampleRepository = PpFakeSampleRepository(effectiveSamples),
            detectionRepository = PpFakeDetectionRepository(
                eggCountsBySession = mapOf(
                    "session-1" to listOf(EggCount("Ascaris", 2)),
                    "session-2" to listOf(EggCount("Ascaris", 1)),
                ),
            ),
            findingDao = ppFindingDao(findingsBySession),
            patientReportPdfBuilder = PatientReportPdfBuilder(),
            patientReportPdfRenderer = renderer,
        )
    }

    private fun ppFindingDao(bySession: Map<String, List<SampleSpeciesFindingEntity>>): SampleSpeciesFindingDao {
        val dao: SampleSpeciesFindingDao = org.mockito.kotlin.mock()
        kotlinx.coroutines.runBlocking {
            bySession.forEach { (sessionId, findings) ->
                org.mockito.kotlin.whenever(dao.getFindingsForSession(sessionId, "user-1")).thenReturn(findings)
            }
        }
        return dao
    }
}

private val PP_FAKE_PDF_BYTES = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

private class PpFakePdfRenderer : PatientReportPdfRenderer {
    var lastDocument: PatientReportPdfDocument? = null

    override suspend fun render(document: PatientReportPdfDocument): ByteArray {
        lastDocument = document
        return PP_FAKE_PDF_BYTES
    }
}

private fun ppSession(id: String, startedAt: LocalDate): Session = Session(
    id = id,
    userId = "user-1",
    deviceId = "device-1",
    startedAt = startedAt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    patientId = "patient-1",
    label = "Session ${id.takeLast(1)}",
)

private fun ppSample(id: String, sessionId: String): Sample = Sample(
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

private fun ppPatient(
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

private fun ppBarangay(): PsgcBarangay = PsgcBarangay(
    code = "0723017001",
    name = "Lahug",
    cityMuniName = "City of Cebu",
    provinceName = null,
    regionName = "Region VII (Central Visayas)",
)

private class PpFakeAuthRepository(
    private val userId: String?,
    private val identity: LocalIdentity? = userId?.let {
        LocalIdentity(userId = it, email = "medtech@example.com", displayName = "Dr. Reyes")
    },
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

private class PpFakePatientRepository(private val patient: Patient?) : PatientRepository {
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

private class PpFakePsgcRepository(private val barangay: PsgcBarangay?) : PsgcRepository {
    override suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay> = emptyList()
    override suspend fun getBarangay(code: String): PsgcBarangay? = barangay
}

private class PpFakeSessionRepository(private val sessions: List<Session>) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> = flowOf(sessions)
    override suspend fun getSessionById(sessionId: String): Session? = sessions.firstOrNull { it.id == sessionId }
    override suspend fun updateSessionLabel(sessionId: String, label: String) = Unit
    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> = emptyList()
    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = false
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
    override suspend fun getSessionsForPatient(patientId: String, userId: String): List<Session> =
        sessions.filter { it.patientId == patientId && it.userId == userId }
}

private class PpFakeSampleRepository(private val samples: List<Sample>) : SampleRepository {
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

private class PpFakeDetectionRepository(
    private val eggCountsBySession: Map<String, List<EggCount>>,
) : DetectionRepository {
    override suspend fun getDetectionsForSample(sampleId: String) =
        emptyList<Detection>()
    override fun observeDetectionsForSample(sampleId: String) =
        flowOf(emptyList<Detection>())
    override suspend fun getConfirmedEggCountsForSession(sessionId: String, userId: String?): List<EggCount> =
        eggCountsBySession[sessionId].orEmpty()
    override suspend fun getSpeciesLabelsForSessions(sessionIds: List<String>): Map<String, List<String>> =
        emptyMap()
}
