package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.FetchOutcomeStore
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.ColleagueDao
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.data.local.entity.ColleagueEntity
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.mapper.SamplePrediction
import com.agarthavision.data.inference.decodePredictions
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.data.supabase.PatientRemoteDataSource
import com.agarthavision.data.supabase.ProfileRemoteDataSource
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.PatientSyncStatus
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FetchRemoteDataUseCase].
 *
 * Robolectric is required because the use case logs failures via [android.util.Log],
 * which throws RuntimeException("Stub!") in plain JVM tests without returnDefaultValues.
 */
// One use case, one fixture of DAOs and remote sources. Splitting by entity type would
// duplicate that fixture five times over and hide the cross-type rules - E2 completeness
// and the E4 skip - which are the point of testing a pull pass whole.
@Suppress("LargeClass")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class FetchRemoteDataUseCaseTest {

    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock()
    // The rows "under these parents" (a linked patient, a session of one) answer with nothing by
    // default, so the suites that predate 14zcqntjt3p keep asserting against the user's own
    // rows, which is what they always stubbed. The scope has its own tests.
    private val patientRemoteDataSource: PatientRemoteDataSource = mock {
        onBlocking { fetchPatients(any()) } doReturn emptyList()
    }
    // Answers "the server holds no model output" by default, so the suites that predate the
    // predictions table keep asserting what they were written to assert.
    private val sampleRemoteDataSource: SampleRemoteDataSource = mock {
        onBlocking { fetchPredictions(any()) } doReturn emptyList()
        onBlocking { fetchSamplesForSessions(any(), any(), any()) } doReturn emptyList()
    }
    private val sessionRemoteDataSource: SessionRemoteDataSource = mock {
        onBlocking { fetchSessionsForPatients(any(), any(), any()) } doReturn emptyList()
    }
    private val reportRemoteDataSource: ReportRemoteDataSource = mock {
        onBlocking { fetchReportsForSessions(any(), any(), any()) } doReturn emptyList()
    }
    // Parents are on the device by default, so the suites that predate the parent check (a
    // colleague's row arriving for a patient or session this device does not hold) keep
    // asserting what they were written to assert. That check has its own tests.
    private val patientDao: PatientDao = mock {
        onBlocking { patientExists(any()) } doReturn true
        onBlocking { getLinksForUser(any()) } doReturn emptyList()
    }
    private val sessionDao: SessionDao = mock {
        onBlocking { sessionExists(any()) } doReturn true
        onBlocking { getSessionIdsOnLinkedPatients(any()) } doReturn emptyList()
    }
    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val sampleSpeciesFindingDao: SampleSpeciesFindingDao = mock()
    private val reportDao: ReportDao = mock()
    private val initialFetchStateStore: InitialFetchStateStore = mock()
    private val fetchOutcomeStore: FetchOutcomeStore = mock()
    private val speciesSuggestionSeeder: SpeciesSuggestionSeeder = mock()

    // Answers "nothing left to fetch" by default, so the suites that predate 86d4by5n9 keep
    // asserting what they were written to assert. The image pass has its own suite.
    private val cacheSampleImages: CacheSampleImagesUseCase = mock {
        onBlocking { invoke(any()) } doReturn ImageCacheSummary()
    }
    private val sampleImageStore: SampleImageStore = mock()
    private val profileRemoteDataSource: ProfileRemoteDataSource = mock {
        onBlocking { fetchColleagues(any()) } doReturn emptyList()
    }
    private val colleagueDao: ColleagueDao = mock {
        onBlocking { getColleagueIdsOnLinkedPatients(any()) } doReturn emptyList()
    }

    private val useCase = FetchRemoteDataUseCase(
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        patientRemoteDataSource = patientRemoteDataSource,
        sampleRemoteDataSource = sampleRemoteDataSource,
        sessionRemoteDataSource = sessionRemoteDataSource,
        reportRemoteDataSource = reportRemoteDataSource,
        patientDao = patientDao,
        sessionDao = sessionDao,
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        sampleSpeciesFindingDao = sampleSpeciesFindingDao,
        reportDao = reportDao,
        initialFetchStateStore = initialFetchStateStore,
        fetchOutcomeStore = fetchOutcomeStore,
        speciesSuggestionSeeder = speciesSuggestionSeeder,
        cacheSampleImages = cacheSampleImages,
        sampleImageStore = sampleImageStore,
        gson = Gson(),
        profileRemoteDataSource = profileRemoteDataSource,
        colleagueDao = colleagueDao,
    )

    // ── Skip conditions ──────────────────────────────────────────────────────

    @Test
    fun `returns Skipped when userId is null`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn(null)
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(FetchSummary.Skipped, result.getOrThrow())
        verify(patientRemoteDataSource, never()).fetchPatientLinks(any(), any(), any())
        verify(sessionRemoteDataSource, never()).fetchOwnSessions(any(), any(), any())
    }

    @Test
    fun `returns Skipped when not authenticated`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(false)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(FetchSummary.Skipped, result.getOrThrow())
        verify(patientRemoteDataSource, never()).fetchPatientLinks(any(), any(), any())
        verify(sessionRemoteDataSource, never()).fetchOwnSessions(any(), any(), any())
    }

    @Test
    fun `returns Skipped when offline`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(FetchSummary.Skipped, result.getOrThrow())
        verify(patientRemoteDataSource, never()).fetchPatientLinks(any(), any(), any())
        verify(sessionRemoteDataSource, never()).fetchOwnSessions(any(), any(), any())
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `happy path returns Ran with correct counts and calls markCompleted`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        val sample = fakeSample("smp-1", "sess-1")
        val report = fakeReport("rep-1", "sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(listOf(sample))
        whenever(sampleRemoteDataSource.fetchDetections(listOf("smp-1"))).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(listOf("smp-1"))).thenReturn(emptyList())
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(listOf(report))
        whenever(reportDao.getReportById("rep-1")).thenReturn(null)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(0, summary.patientsFetched)
        assertEquals(1, summary.sessionsFetched)
        assertEquals(1, summary.samplesFetched)
        assertEquals(1, summary.reportsFetched)
        verify(initialFetchStateStore).markCompleted("user-1")
    }

    // ── Patients (PB-05d) ────────────────────────────────────────────────────

    @Test
    fun `a local PENDING patient is not overwritten by a remote pull of the same id`() = runTest {
        setupOnlineSignedIn()
        serverAssigns(fakePatient("pat-1"))
        whenever(patientDao.getPatientById("pat-1"))
            .thenReturn(fakePatient("pat-1", PatientSyncStatus.PENDING.value))
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        // That row is a patient the medtech typed in offline. Clobbering it loses them.
        verify(patientDao, never()).upsertPatient(any())
        assertEquals(0, (result.getOrThrow() as FetchSummary.Ran).patientsFetched)
    }

    @Test
    fun `a local SYNC_FAILED patient is not overwritten either`() = runTest {
        setupOnlineSignedIn()
        serverAssigns(fakePatient("pat-1"))
        whenever(patientDao.getPatientById("pat-1"))
            .thenReturn(fakePatient("pat-1", PatientSyncStatus.SYNC_FAILED.value))
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(patientDao, never()).upsertPatient(any())
    }

    @Test
    fun `an absent patient is inserted along with its link rows`() = runTest {
        setupOnlineSignedIn()
        serverAssigns(fakePatient("pat-1"))
        whenever(patientDao.getPatientById("pat-1")).thenReturn(null)
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        verify(patientDao).upsertPatient(any())
        // Without the link row the patient is on the device and invisible to every read.
        verify(patientDao).linkPatientsToUsers(any())
        assertEquals(1, (result.getOrThrow() as FetchSummary.Ran).patientsFetched)
    }

    // ── Assignments (14zcqntjph5) ────────────────────────────────────────────

    @Test
    fun `an assignment the server no longer returns is removed and the patient stays`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(patientDao.getLinksForUser("user-1"))
            .thenReturn(listOf(PatientUserEntity("pat-gone", "user-1", 1_700_000_000_000)))
        whenever(patientDao.getPatientById("pat-gone")).thenReturn(fakePatient("pat-gone"))

        val result = useCase.invoke()

        // An admin reassigned the patient away. The access row goes; the patient and its
        // records are untouched (C8), so nothing is deleted but the link.
        verify(patientDao).unlinkPatientFromUser("pat-gone", "user-1")
        verify(patientDao, never()).deletePatient(any())
        assertTrue((result.getOrThrow() as FetchSummary.Ran).isComplete)
    }

    @Test
    fun `a patient still waiting to push keeps its link`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(patientDao.getLinksForUser("user-1"))
            .thenReturn(listOf(PatientUserEntity("pat-new", "user-1", 1_700_000_000_000)))
        whenever(patientDao.getPatientById("pat-new"))
            .thenReturn(fakePatient("pat-new", PatientSyncStatus.PENDING.value))

        useCase.invoke()

        // Created offline: the server has not heard of it, so its absence there proves nothing.
        verify(patientDao, never()).unlinkPatientFromUser(any(), any())
    }

    @Test
    fun `a link that is still on the server is kept`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        val link = PatientUserEntity("pat-1", "user-1", 1_700_000_000_000)
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenReturn(listOf(link))
        whenever(patientDao.getLinksForUser("user-1")).thenReturn(listOf(link))

        useCase.invoke()

        verify(patientDao, never()).unlinkPatientFromUser(any(), any())
    }

    // ── Colleagues' names (14zcqntjph6) ──────────────────────────────────────

    @Test
    fun `colleagues' names are cached for read-only records`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        val names = listOf(ColleagueEntity(userId = "user-2", fullName = "Maria Santos"))
        whenever(colleagueDao.getColleagueIdsOnLinkedPatients("user-1")).thenReturn(listOf("user-2"))
        whenever(profileRemoteDataSource.fetchColleagues(listOf("user-2"))).thenReturn(names)

        useCase.invoke()

        verify(colleagueDao).upsertColleagues(names)
    }

    @Test
    fun `a failed name fetch does not fail the pass`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(colleagueDao.getColleagueIdsOnLinkedPatients("user-1")).thenReturn(listOf("user-2"))
        whenever(profileRemoteDataSource.fetchColleagues(listOf("user-2"))).thenThrow(RuntimeException("boom"))

        val result = useCase.invoke()

        // A missing name costs a label on a read-only card, not a record.
        assertTrue((result.getOrThrow() as FetchSummary.Ran).isComplete)
        verify(initialFetchStateStore).markCompleted("user-1")
    }

    // ── Scope: the user's own patients and nothing else (14zcqntjt3p) ────────
    //
    // An org admin's policies hand them their whole laboratory, and a super admin's hand them
    // everything. These pin that the pull never asks the server for more than a medtech's
    // policies would give: the user's own rows, plus the full history of the patients their own
    // links name.

    @Test
    fun `only the user's own links are read, and only the patients they name are fetched`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        serverAssigns(fakePatient("pat-1"))
        whenever(patientDao.getPatientById("pat-1")).thenReturn(null)

        useCase.invoke()

        verify(patientRemoteDataSource).fetchPatientLinks("user-1", 0L, 500L)
        verify(patientRemoteDataSource).fetchPatients(listOf("pat-1"))
        verify(patientRemoteDataSource, times(1)).fetchPatients(any())
        val stored = argumentCaptor<PatientEntity>()
        verify(patientDao).upsertPatient(stored.capture())
        assertEquals(listOf("pat-1"), stored.allValues.map { it.patientId })
    }

    @Test
    fun `no links means no patient is fetched at all`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()

        useCase.invoke()

        verify(patientRemoteDataSource, never()).fetchPatients(any())
        verify(patientDao, never()).upsertPatient(any())
    }

    @Test
    fun `sessions are the user's own plus those of the patients they are linked to`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(patientDao.getLinksForUser("user-1"))
            .thenReturn(listOf(PatientUserEntity("pat-1", "user-1", 1_700_000_000_000)))

        useCase.invoke()

        verify(sessionRemoteDataSource).fetchOwnSessions("user-1", 0L, 500L)
        verify(sessionRemoteDataSource).fetchSessionsForPatients(listOf("pat-1"), 0L, 500L)
        verify(sessionRemoteDataSource, times(1)).fetchSessionsForPatients(any(), any(), any())
    }

    @Test
    fun `samples and reports are the user's own plus those under their patients' sessions`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(sessionDao.getSessionIdsOnLinkedPatients("user-1")).thenReturn(listOf("sess-1"))

        useCase.invoke()

        verify(sampleRemoteDataSource).fetchOwnSamples("user-1", 0L, 500L)
        verify(sampleRemoteDataSource).fetchSamplesForSessions(listOf("sess-1"), 0L, 500L)
        verify(reportRemoteDataSource).fetchOwnReports("user-1", 0L, 500L)
        verify(reportRemoteDataSource).fetchReportsForSessions(listOf("sess-1"), 0L, 500L)
    }

    @Test
    fun `a user linked to no patient asks for nothing under a parent`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()

        useCase.invoke()

        verify(sessionRemoteDataSource, never()).fetchSessionsForPatients(any(), any(), any())
        verify(sampleRemoteDataSource, never()).fetchSamplesForSessions(any(), any(), any())
        verify(reportRemoteDataSource, never()).fetchReportsForSessions(any(), any(), any())
    }

    @Test
    fun `a colleague's session on a linked patient is stored`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(patientDao.getLinksForUser("user-1"))
            .thenReturn(listOf(PatientUserEntity("patient-1", "user-1", 1_700_000_000_000)))
        val colleagueSession = fakeSession("sess-c").copy(userId = "user-2")
        whenever(sessionRemoteDataSource.fetchSessionsForPatients(listOf("patient-1"), 0L, 500L))
            .thenReturn(listOf(colleagueSession))
        whenever(sessionDao.getSessionById("sess-c")).thenReturn(null)

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        // The shared history 0007 opened (14zcqntjph5) survives the narrower fetch.
        verify(sessionDao).upsertSession(colleagueSession)
        assertEquals(1, summary.sessionsFetched)
    }

    @Test
    fun `a row that is both the user's own and under a linked parent is written once`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(patientDao.getLinksForUser("user-1"))
            .thenReturn(listOf(PatientUserEntity("patient-1", "user-1", 1_700_000_000_000)))
        whenever(sessionDao.getSessionIdsOnLinkedPatients("user-1")).thenReturn(listOf("sess-1"))
        val session = fakeSession("sess-1")
        val sample = fakeSample("smp-1", "sess-1")
        val report = fakeReport("rep-1", "sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionRemoteDataSource.fetchSessionsForPatients(listOf("patient-1"), 0L, 500L))
            .thenReturn(listOf(session))
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(listOf(sample))
        whenever(sampleRemoteDataSource.fetchSamplesForSessions(listOf("sess-1"), 0L, 500L))
            .thenReturn(listOf(sample))
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(listOf(report))
        whenever(reportRemoteDataSource.fetchReportsForSessions(listOf("sess-1"), 0L, 500L))
            .thenReturn(listOf(report))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(reportDao.getReportById("rep-1")).thenReturn(null)

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        verify(sessionDao, times(1)).upsertSession(any())
        verify(sampleDao, times(1)).upsertSample(any())
        verify(reportDao, times(1)).insertReport(any())
        verify(sampleRemoteDataSource, times(1)).fetchDetections(any())
        assertEquals(1, summary.sessionsFetched)
        assertEquals(1, summary.samplesFetched)
        assertEquals(1, summary.reportsFetched)
    }

    @Test
    fun `parent ids are sent a hundred at a time`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(sessionDao.getSessionIdsOnLinkedPatients("user-1")).thenReturn((1..150).map { "sess-$it" })

        useCase.invoke()

        // Keeps the query string short (E5), the same bound the child rows already use.
        verify(sampleRemoteDataSource, times(2)).fetchSamplesForSessions(any(), any(), any())
        verify(reportRemoteDataSource, times(2)).fetchReportsForSessions(any(), any(), any())
    }

    @Test
    fun `no colleague on the user's patients means no profile is fetched`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()

        useCase.invoke()

        verify(profileRemoteDataSource, never()).fetchColleagues(any())
        verify(colleagueDao, never()).upsertColleagues(any())
    }

    // ── Rows whose parent is not on the device (14zcqntjph5) ─────────────────

    @Test
    fun `a session whose patient is not on the device is skipped without failing the pass`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(fakeSession("sess-1")))
        whenever(patientDao.patientExists("patient-1")).thenReturn(false)

        val result = useCase.invoke()

        // The server still shows an author their own sessions of a patient they were
        // unassigned from. Writing one would break the local patient foreign key, which used to
        // fail every session on every pass.
        verify(sessionDao, never()).upsertSession(any())
        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(0, summary.sessionsFetched)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `a sample or report whose session is not on the device is skipped`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
            .thenReturn(listOf(fakeSample("smp-1", "sess-x")))
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(listOf(fakeReport("rep-1", "sess-x")))
        whenever(sessionDao.sessionExists("sess-x")).thenReturn(false)

        val result = useCase.invoke()

        verify(sampleDao, never()).upsertSample(any())
        verify(reportDao, never()).insertReport(any())
        assertTrue((result.getOrThrow() as FetchSummary.Ran).isComplete)
    }

    @Test
    fun `a failed patient pull leaves markCompleted unset`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenThrow(RuntimeException("boom"))
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        // E2: a partial pull must leave the badge honest rather than claim a cache the
        // device does not have. The medtech finds out where there is no signal.
        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `a failed pull names the type that failed and records the pass as incomplete`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenThrow(RuntimeException("boom"))
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        // A count of zero used to be the only evidence, and it reads the same whether nothing
        // was new or everything threw. The worker decides whether to retry on this.
        assertEquals(setOf(FetchType.PATIENTS), summary.failed)
        assertFalse(summary.isComplete)
        verify(fetchOutcomeStore).record(userId = "user-1", complete = false)
    }

    @Test
    fun `a clean pull reports complete and clears the flag`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        assertTrue(summary.isComplete)
        verify(fetchOutcomeStore).record(userId = "user-1", complete = true)
    }

    @Test
    fun `fetches patients before sessions (FK-safe order)`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // A session arriving before its patient violates the local FK on sessions.patient_id.
        inOrder(patientRemoteDataSource, sessionRemoteDataSource) {
            verify(patientRemoteDataSource).fetchPatientLinks("user-1")
            verify(sessionRemoteDataSource).fetchOwnSessions("user-1")
        }
    }

    // ── Species index refresh (PB-08a) ───────────────────────────────────────

    @Test
    fun `a successful pass folds new species into the offline index`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // The two reference caches have to stay in step: a species that arrived with this
        // pass must be suggestible on the next offline verification.
        verify(speciesSuggestionSeeder).refresh()
    }

    @Test
    fun `a pass whose samples failed still refreshes the species index`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
            .thenThrow(RuntimeException("boom"))
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // PB-08b says "refreshed on each successful fetch pass", and the seeder re-derives
        // from rows the device already holds rather than from what this pass brought down.
        // Gating it on samples left the index stale after a pass that pulled patients and
        // reports fine. It never throws, so it cannot turn a partial pass into a failed one.
        verify(speciesSuggestionSeeder).refresh()
    }

    @Test
    fun `a skipped pass does not refresh the species index`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        useCase.invoke()

        verify(speciesSuggestionSeeder, never()).refresh()
    }

    // ── FK-safe order: sessions before samples ───────────────────────────────

    @Test
    fun `fetches sessions before samples (FK-safe order)`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // sessions must be fetched first, then samples
        val order = inOrder(sessionRemoteDataSource, sampleRemoteDataSource, reportRemoteDataSource)
        order.verify(sessionRemoteDataSource).fetchOwnSessions("user-1")
        order.verify(sampleRemoteDataSource).fetchOwnSamples(any(), any(), any())
        order.verify(reportRemoteDataSource).fetchOwnReports("user-1")
    }

    // ── markCompleted only on full success ───────────────────────────────────

    @Test
    fun `markCompleted NOT called when sessions fetch throws`() = runTest {
        setupOnlineSignedIn()
        // thenAnswer (not thenThrow) is required for suspend functions in mockito-kotlin
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1"))
            .thenAnswer { throw IllegalStateException("network") }
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess) // outer runCatching absorbs per-type failures
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `markCompleted NOT called when samples fetch throws`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
            .thenAnswer { throw IllegalStateException("network") }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `markCompleted NOT called when reports fetch throws`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1"))
            .thenAnswer { throw IllegalStateException("network") }

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `other entity types still run even when one throws (partial-failure isolation)`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1"))
            .thenAnswer { throw IllegalStateException("sessions broken") }
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // samples and reports must still be attempted even though sessions threw
        verify(sampleRemoteDataSource).fetchOwnSamples(any(), any(), any())
        verify(reportRemoteDataSource).fetchOwnReports(any(), any(), any())
    }

    // ── E4 skip-guard ────────────────────────────────────────────────────────

    @Test
    fun `E4 - inserts sample when local is absent`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)

        useCase.invoke()

        verify(sampleDao).upsertSample(sample)
    }

    @Test
    fun `E4 - inserts sample when local status is SYNCED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localSynced = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNCED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localSynced)

        useCase.invoke()

        verify(sampleDao).upsertSample(sample)
    }

    @Test
    fun `E4 - skips insert when local sample is VERIFIED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localVerified = fakeSample("smp-1", "sess-1", status = SampleStatus.VERIFIED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localVerified)

        useCase.invoke()

        verify(sampleDao, never()).upsertSample(any())
    }

    @Test
    fun `E4 - skips insert when local sample is SYNC_FAILED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localFailed = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNC_FAILED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localFailed)

        useCase.invoke()

        verify(sampleDao, never()).upsertSample(any())
    }

    @Test
    fun `E4 - skips insert when local sample is FLAGGED (in-progress work)`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localFlagged = fakeSample("smp-1", "sess-1", status = SampleStatus.FLAGGED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localFlagged)

        useCase.invoke()

        verify(sampleDao, never()).upsertSample(any())
    }

    @Test
    fun `E4 child guard - VERIFIED sample skipped parent also skips fetchDetections and fetchFindings`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localVerified = fakeSample("smp-1", "sess-1", status = SampleStatus.VERIFIED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localVerified)

        useCase.invoke()

        // The expert verdict / expertClass payload must not be clobbered
        verify(sampleRemoteDataSource, never()).fetchDetections(any())
        verify(sampleRemoteDataSource, never()).fetchFindings(any())
        verify(detectionDao, never()).insertDetections(any())
        verify(sampleSpeciesFindingDao, never()).insertFindings(any())
        // The findings replace is scoped to written ids, so a skipped sample must not appear in
        // one at all - it clears rows, and this sample's are unsynced work.
        verify(sampleSpeciesFindingDao, never()).replaceFindingsForSamples(any(), any())
    }

    @Test
    fun `E4 child guard - SYNC_FAILED sample skipped parent also skips fetchDetections and fetchFindings`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localFailed = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNC_FAILED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localFailed)

        useCase.invoke()

        verify(sampleRemoteDataSource, never()).fetchDetections(any())
        verify(sampleRemoteDataSource, never()).fetchFindings(any())
        verify(detectionDao, never()).insertDetections(any())
        verify(sampleSpeciesFindingDao, never()).insertFindings(any())
        verify(sampleSpeciesFindingDao, never()).replaceFindingsForSamples(any(), any())
    }

    @Test
    fun `E4 child guard - inserted (absent) sample fetches and inserts children`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)

        useCase.invoke()

        verify(sampleRemoteDataSource).fetchDetections(listOf("smp-1"))
        verify(sampleRemoteDataSource).fetchFindings(listOf("smp-1"))
    }

    @Test
    fun `E4 child guard - inserted (SYNCED) sample fetches and inserts children`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localSynced = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNCED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localSynced)

        useCase.invoke()

        verify(sampleRemoteDataSource).fetchDetections(listOf("smp-1"))
        verify(sampleRemoteDataSource).fetchFindings(listOf("smp-1"))
    }

    @Test
    fun `E4 child guard - mixed page only fetches children for inserted samples not skipped ones`() = runTest {
        setupOnlineSignedIn()
        val inserted = fakeSample("smp-inserted", "sess-1")
        val skipped = fakeSample("smp-skipped", "sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
            .thenReturn(listOf(inserted, skipped))
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-inserted")).thenReturn(null)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-skipped"))
            .thenReturn(fakeSample("smp-skipped", "sess-1", status = SampleStatus.VERIFIED.value))
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // Only the inserted id must appear in the child fetch; the VERIFIED id must be absent
        verify(sampleRemoteDataSource).fetchDetections(listOf("smp-inserted"))
        verify(sampleRemoteDataSource).fetchFindings(listOf("smp-inserted"))
    }

    // ── child reconciliation under @Upsert (86d4bx196) ───────────────────────

    @Test
    fun `a sample the server holds no findings for has its local findings cleared`() = runTest {
        // The case the naive @Upsert swap would have got wrong. Under the old
        // @Insert(REPLACE) the parent write cascaded the findings away before this ran, so a
        // species removed on another device disappeared here for free. @Upsert leaves them, so
        // the replace has to be keyed on the written sample ids rather than on the ids present
        // in the fetched findings - otherwise a cleared field keeps its stale count forever.
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchFindings(listOf("smp-1"))).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleSpeciesFindingDao).replaceFindingsForSamples(listOf("smp-1"), emptyList())
    }

    @Test
    fun `fetched findings are replaced against the sample they belong to`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        val finding = SampleSpeciesFindingEntity(
            findingId = "fnd-1",
            sampleId = "smp-1",
            species = "Ascaris lumbricoides",
            eggCount = 23,
        )
        whenever(sampleRemoteDataSource.fetchFindings(listOf("smp-1"))).thenReturn(listOf(finding))

        useCase.invoke()

        verify(sampleSpeciesFindingDao).replaceFindingsForSamples(listOf("smp-1"), listOf(finding))
    }

    @Test
    fun `detections merge rather than being replaced`() = runTest {
        // The asymmetry is deliberate: detections are the retraining corpus C8 protects, and the
        // push side never deletes one, so the server's set is a superset of anything this device
        // pushed. Nothing in the pull path is allowed to remove a detection row - and DetectionDao
        // exposes no delete for one to call, which is the structural half of the same rule.
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        val detection = DetectionEntity(
            detectionId = "det-1",
            sampleId = "smp-1",
            classLabel = "Ascaris lumbricoides",
            confidence = 0.9f,
            bboxX = 1f,
            bboxY = 2f,
            bboxW = 3f,
            bboxH = 4f,
        )
        whenever(sampleRemoteDataSource.fetchDetections(listOf("smp-1")))
            .thenReturn(listOf(detection))

        useCase.invoke()

        verify(detectionDao).insertDetections(listOf(detection))
    }

    // ── Model output (predictions) ───────────────────────────────────────────

    private fun remotePrediction(sampleId: String, ordinal: Int, x: Float) = SamplePrediction(
        sampleId = sampleId,
        ordinal = ordinal,
        prediction = Prediction(
            classLabel = "Ascaris",
            confidence = 0.8f,
            x = x,
            y = 1f,
            width = 2f,
            height = 3f,
        ),
    )

    /**
     * **The bug this closes.** The server's sample row has no predictions_json, so every pulled
     * row mapped it to null, and the upsert wrote that null over the capturing device's own
     * model output on the first pass after its push landed.
     */
    @Test
    fun `a pull keeps the model output the device already holds`() = runTest {
        setupOnlineSignedIn()
        val remote = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(remote))
        val local = fakeSample("smp-1", "sess-1").copy(predictionsJson = "[local]")
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(local)

        useCase.invoke()

        verify(sampleDao).upsertSample(remote.copy(predictionsJson = "[local]"))
        verify(sampleDao, never()).updatePredictionsJson(any(), any())
    }

    /** A sample verified elsewhere arrives with the model's real output, not a rebuild of it. */
    @Test
    fun `a whole set of predictions is folded into the pulled sample`() = runTest {
        setupOnlineSignedIn()
        setupMinimalFetch(samples = listOf(fakeSample("smp-1", "sess-1")))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchPredictions(listOf("smp-1"))).thenReturn(
            listOf(remotePrediction("smp-1", 1, 20f), remotePrediction("smp-1", 0, 10f)),
        )

        useCase.invoke()

        val json = argumentCaptor<String>()
        verify(sampleDao).updatePredictionsJson(eq("smp-1"), json.capture())
        val restored = Gson().decodePredictions(json.firstValue)
        assertEquals(listOf(10f, 20f), restored?.map { it.x })
        assertEquals("Ascaris", restored?.first()?.classLabel)
    }

    /** A partial set would shift ordinals; the device's copy (here, none) is left alone. */
    @Test
    fun `a set with a missing ordinal is not written`() = runTest {
        setupOnlineSignedIn()
        setupMinimalFetch(samples = listOf(fakeSample("smp-1", "sess-1")))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchPredictions(listOf("smp-1"))).thenReturn(
            listOf(remotePrediction("smp-1", 0, 10f), remotePrediction("smp-1", 2, 30f)),
        )

        useCase.invoke()

        verify(sampleDao, never()).updatePredictionsJson(any(), any())
    }

    /** Skipped parents skip their predictions too — the E4 guard covers every child table. */
    @Test
    fun `a VERIFIED local sample does not fetch predictions`() = runTest {
        setupOnlineSignedIn()
        setupMinimalFetch(samples = listOf(fakeSample("smp-1", "sess-1")))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1"))
            .thenReturn(fakeSample("smp-1", "sess-1", status = SampleStatus.VERIFIED.value))

        useCase.invoke()

        verify(sampleRemoteDataSource, never()).fetchPredictions(any())
    }

    /**
     * Predictions are fetched before detections, so a failure stops the pass before a detection
     * lands. A sample with detections and no model output would reopen by rebuilding from the
     * rows, and a rejected box with no geometry is a gap that rebuild cannot fill.
     */
    @Test
    fun `a failed predictions fetch writes no detections and fails the samples pull`() = runTest {
        setupOnlineSignedIn()
        setupMinimalFetch(samples = listOf(fakeSample("smp-1", "sess-1")))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchPredictions(any())).thenThrow(RuntimeException("relation does not exist"))

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        verify(sampleRemoteDataSource, never()).fetchDetections(any())
        verify(detectionDao, never()).insertDetections(any())
        assertTrue(FetchType.SAMPLES in summary.failed)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    // ── Child-fetch chunking (isIn URL-length guard) ─────────────────────────

    @Test
    fun `chunked - 150 inserted ids produce 2 fetchDetections and 2 fetchFindings calls`() = runTest {
        setupOnlineSignedIn()
        val page = (1..150).map { fakeSample("smp-$it", "sess-1") }
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // 150 ids / 100 per batch = 2 chunks → 2 calls each
        verify(sampleRemoteDataSource, times(2)).fetchDetections(any())
        verify(sampleRemoteDataSource, times(2)).fetchFindings(any())
    }

    @Test
    fun `chunked - exactly 100 inserted ids produce exactly 1 fetchDetections call`() = runTest {
        setupOnlineSignedIn()
        val page = (1..100).map { fakeSample("smp-$it", "sess-1") }
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, times(1)).fetchDetections(any())
        verify(sampleRemoteDataSource, times(1)).fetchFindings(any())
    }

    @Test
    fun `E4 session guard - inserts when local session is absent`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sessionDao).upsertSession(session)
    }

    @Test
    fun `E4 session guard - inserts when local session has SYNCED status`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        val localSynced = fakeSession("sess-1", supabaseStatus = SessionSyncStatus.SYNCED.value)
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(localSynced)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sessionDao).upsertSession(session)
    }

    @Test
    fun `E4 session guard - skips insert when local session is PENDING`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        val localPending = fakeSession("sess-1", supabaseStatus = SessionSyncStatus.PENDING.value)
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(localPending)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sessionDao, never()).upsertSession(any())
    }

    // ── Label-collision reconciliation in pullSessions (86d4bzjhw) ──────────

    @Test
    fun `pullSessions - label collision is disambiguated and both rows are counted`() = runTest {
        setupOnlineSignedIn()
        val collidingLabel = "SMEAR-1"
        // Two sessions from the same patient, same label — exactly the pre-existing collision
        // that production Supabase may hold from before the unique index was added.
        val session1 = fakeSession("sess-abcd").copy(label = collidingLabel)
        val session2 = fakeSession("sess-efgh").copy(label = collidingLabel)
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session1, session2))
        whenever(sessionDao.getSessionById("sess-abcd")).thenReturn(null)
        whenever(sessionDao.getSessionById("sess-efgh")).thenReturn(null)
        // sess-abcd: no collision — pre-check returns 0.
        whenever(sessionDao.countLabelCollisions("patient-1", collidingLabel, "sess-abcd"))
            .thenReturn(0)
        // sess-efgh: collides with the already-inserted sess-abcd — pre-check returns 1.
        // This is the accurate model of what countLabelCollisions returns after sess-abcd lands.
        whenever(sessionDao.countLabelCollisions("patient-1", collidingLabel, "sess-efgh"))
            .thenReturn(1)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals("both sessions must be counted even when one needs disambiguation", 2, summary.sessionsFetched)
        assertFalse(
            "sessions pull must not be flagged as failed when only a label was remapped",
            FetchType.SESSIONS in summary.failed,
        )
        // sess-abcd lands with original label (no collision).
        verify(sessionDao).upsertSession(session1)
        // The disambiguated label is: original.trim() + "-" + sessionId.take(4), all uppercase.
        val expectedDisambiguated = "${collidingLabel}-${session2.sessionId.take(4)}".uppercase()
        verify(sessionDao).upsertSession(session2.copy(label = expectedDisambiguated))
    }

    @Test
    fun `a colleague's session that clashes only with this device's stale label keeps its label`() = runTest {
        // 14zcqntjph7: both phones minted S03 offline. The server kept the colleague's S03 and
        // renamed this device's to S03-SESS on push. The colleague's row arrives first (pages are
        // oldest first) while the local row still reads S03; settling clashes after the page
        // lets the renamed own row land first, so nothing is suffixed that the server left alone.
        setupOnlineSignedIn()
        stubEmptyPulls()
        val localLabels = mutableMapOf("sess-mine" to "S03")
        val theirs = fakeSession("sess-theirs").copy(userId = "user-2", label = "S03")
        val mine = fakeSession("sess-mine").copy(label = "S03-SESS")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(theirs, mine))
        whenever(sessionDao.getSessionById("sess-theirs")).thenReturn(null)
        whenever(sessionDao.getSessionById("sess-mine")).thenReturn(fakeSession("sess-mine").copy(label = "S03"))
        whenever(sessionDao.countLabelCollisions(any(), any(), any())).thenAnswer { call ->
            val label = call.getArgument<String>(1)
            val excluding = call.getArgument<String>(2)
            localLabels.count { (id, held) -> id != excluding && held == label }
        }
        whenever(sessionDao.upsertSession(any())).thenAnswer { call ->
            val written = call.getArgument<SessionEntity>(0)
            localLabels[written.sessionId] = written.label.orEmpty()
            Unit
        }

        useCase.invoke()

        verify(sessionDao).upsertSession(mine)
        verify(sessionDao).upsertSession(theirs)
        assertEquals(mapOf("sess-mine" to "S03-SESS", "sess-theirs" to "S03"), localLabels)
    }

    @Test
    fun `pullSessions - non-constraint exception from upsertSession is not swallowed`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1").copy(label = "SMEAR-1")
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        // A non-constraint exception — database is closed, disk full, etc. — must NOT be
        // silently swallowed by the new catch block; it must surface as a sessions failure.
        whenever(sessionDao.upsertSession(session))
            .thenAnswer { throw IllegalStateException("database is closed") }
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess) // outer runCatching absorbs per-type failures
        val summary = result.getOrThrow() as FetchSummary.Ran
        assertTrue(
            "a non-constraint exception must surface as FetchType.SESSIONS in failed",
            FetchType.SESSIONS in summary.failed,
        )
        assertFalse(summary.isComplete)
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    @Test
    fun `paginates - full page (500 rows) triggers a second fetch`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        // First page: exactly 500 rows → triggers another request
        val page1 = (1..500).map { fakeSample("smp-$it", "sess-1") }
        // Second page: 0 rows → stops
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(page1)
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 500L, 500L)).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page1.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(500, summary.samplesFetched)
        verify(sampleRemoteDataSource, times(2)).fetchOwnSamples(any(), any(), any())
    }

    @Test
    fun `paginates - short page stops after one request`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        val page = (1..3).map { fakeSample("smp-$it", "sess-1") }
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, times(1)).fetchOwnSamples(any(), any(), any())
    }

    @Test
    fun `paginates links and chunks patients - 500 links take two link pages and five patient fetches`() = runTest {
        setupOnlineSignedIn()
        val patients = (1..500).map { fakePatient("pat-$it") }
        val links = patients.map { PatientUserEntity(it.patientId, "user-1", 1_700_000_000_000) }
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1", 0L, 500L)).thenReturn(links)
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1", 500L, 500L)).thenReturn(emptyList())
        patients.chunked(100).forEach { chunk ->
            whenever(patientRemoteDataSource.fetchPatients(chunk.map { it.patientId })).thenReturn(chunk)
        }
        patients.forEach { whenever(patientDao.getPatientById(it.patientId)).thenReturn(null) }
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(500, summary.patientsFetched)
        verify(patientRemoteDataSource, times(2)).fetchPatientLinks(any(), any(), any())
        // By id, a hundred at a time, so the query string stays short (E5).
        verify(patientRemoteDataSource, times(5)).fetchPatients(any())
    }

    @Test
    fun `paginates sessions - full page (500 rows) triggers a second fetch`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenReturn(emptyList())
        val page1 = (1..500).map { fakeSession("sess-$it", "pat-1") }
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1", 0L, 500L)).thenReturn(page1)
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1", 500L, 500L)).thenReturn(emptyList())
        page1.forEach { whenever(sessionDao.getSessionById(it.sessionId)).thenReturn(null) }
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(500, summary.sessionsFetched)
        verify(sessionRemoteDataSource, times(2)).fetchOwnSessions(any(), any(), any())
    }

    @Test
    fun `paginates reports - full page (500 rows) triggers a second fetch`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenReturn(emptyList())
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        val page1 = (1..500).map { fakeReport("rep-$it", "sess-1") }
        whenever(reportRemoteDataSource.fetchOwnReports("user-1", 0L, 500L)).thenReturn(page1)
        whenever(reportRemoteDataSource.fetchOwnReports("user-1", 500L, 500L)).thenReturn(emptyList())
        page1.forEach { whenever(reportDao.getReportById(it.reportId)).thenReturn(null) }

        val result = useCase.invoke()

        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(500, summary.reportsFetched)
        verify(reportRemoteDataSource, times(2)).fetchOwnReports(any(), any(), any())
    }

    @Test
    fun `empty sample page does not call fetchDetections or fetchFindings`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, never()).fetchDetections(any())
        verify(sampleRemoteDataSource, never()).fetchFindings(any())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun setupOnlineSignedIn() {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)
        // Patients default to an empty pull: no links, so no patients. Leaving the links
        // unstubbed would make pullPatients throw on a null List, which runCatching swallows
        // into patientsOk = false — and that silently suppresses markCompleted in every
        // test below rather than failing the one that is actually wrong.
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1")).thenReturn(emptyList())
    }

    private fun fakePatient(
        id: String,
        supabaseStatus: String = PatientSyncStatus.SYNCED.value,
    ) = PatientEntity(
        patientId = id,
        lastname = "Cruz",
        firstname = "Gerald",
        middleName = null,
        sex = "M",
        birthdate = 0L,
        psgcBarangayCode = "0102801001",
        createdBy = "user-1",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000,
        supabaseStatus = supabaseStatus,
    )

    /** Configures the minimal stubs for a fetch pass with provided samples; sessions and reports are empty. */
    private suspend fun setupMinimalFetch(samples: List<SampleEntity>) {
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(samples)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())
    }

    private fun fakeSession(
        id: String,
        supabaseStatus: String = SessionSyncStatus.SYNCED.value,
    ) = SessionEntity(
        sessionId = id,
        userId = "user-1",
        patientId = "patient-1",
        deviceId = "device-1",
        startedAt = 1_000L,
        supabaseStatus = supabaseStatus,
    )

    /** The server assigns [patients] to user-1: their links, and the rows those links name. */
    private suspend fun serverAssigns(vararg patients: PatientEntity) {
        whenever(patientRemoteDataSource.fetchPatientLinks("user-1"))
            .thenReturn(patients.map { PatientUserEntity(it.patientId, "user-1", 1_700_000_000_000) })
        whenever(patientRemoteDataSource.fetchPatients(patients.map { it.patientId }))
            .thenReturn(patients.toList())
    }

    /** Every pull answers with nothing, so a test only stubs the one it is about. */
    private suspend fun stubEmptyPulls() {
        whenever(sessionRemoteDataSource.fetchOwnSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(emptyList())
    }

    private fun fakeSample(
        id: String,
        sessionId: String,
        status: String = SampleStatus.SYNCED.value,
    ) = SampleEntity(
        sampleId = id,
        sessionId = sessionId,
        userId = "user-1",
        deviceId = "",
        timestamp = 1_000L,
        imagePath = "",
        storagePath = "$id.jpg",
        status = status,
    )

    private fun fakeReport(id: String, sessionId: String) = ReportEntity(
        reportId = id,
        sessionId = sessionId,
        userId = "user-1",
        generatedAt = 1_000L,
        totalSamples = 0,
        totalEggsConfirmed = 0,
        positiveSpeciesJson = "[]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        supabaseStatus = ReportSyncStatus.SYNCED.value,
        createdAt = 1_000L,
    )

    // ── Report files (86d4bzm9g) ─────────────────────────────────────────────

    @Test
    fun `a pull does not undo a restored report file`() = runTest {
        // Found on device: a restore repoints the row at the file it downloaded, and the next
        // pull wrote the generating device's MediaStore id straight back. The report then opened
        // to a file that is not here — and fetched it again, leaving another copy behind.
        setupOnlineSignedIn()
        stubEmptyPulls()
        val remote = fakeReport("rep-1", "sess-1").copy(
            pdfFilePath = "content://media/external_primary/file/1000929272",
        )
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(listOf(remote))
        whenever(reportDao.getReportById("rep-1")).thenReturn(
            remote.copy(pdfFilePath = "content://media/external_primary/file/1000929275"),
        )

        useCase.invoke()

        val written = argumentCaptor<ReportEntity>()
        verify(reportDao).insertReport(written.capture())
        assertEquals("content://media/external_primary/file/1000929275", written.firstValue.pdfFilePath)
    }

    @Test
    fun `a report this device has never held takes the remote paths`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        val remote = fakeReport("rep-1", "sess-1").copy(
            pdfFilePath = "content://media/external_primary/file/1000929272",
        )
        whenever(reportRemoteDataSource.fetchOwnReports("user-1")).thenReturn(listOf(remote))
        whenever(reportDao.getReportById("rep-1")).thenReturn(null)

        useCase.invoke()

        // Stale here, but it is what lets the open fall through to a restore: a null path would
        // read as "this report was never generated as a PDF".
        val written = argumentCaptor<ReportEntity>()
        verify(reportDao).insertReport(written.capture())
        assertEquals("content://media/external_primary/file/1000929272", written.firstValue.pdfFilePath)
    }

    // ── Sample frames (86d4by5n9) ────────────────────────────────────────────

    @Test
    fun `a pull does not orphan the JPEG this device already holds`() = runTest {
        // The live defect this ticket had to fix before it could cache anything. Every pulled
        // sample is mapped with image_path = "" - correct, the server has no notion of this
        // device's disk - and a sample captured *here* goes SYNCED the moment its push lands,
        // so the E4 guard lets the next pull overwrite it. The JPEG stayed on disk with nothing
        // pointing at it, and the capturing device fell back to needing a network for a frame
        // already in its hands.
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
            .thenReturn(listOf(fakeSample("smp-1", "session-1")))
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(sampleImageStore.cachedPathOrNull("user-1", "smp-1"))
            .thenReturn("/data/users/user-1/samples/smp-1.jpg")

        useCase.invoke()

        val written = argumentCaptor<SampleEntity>()
        verify(sampleDao).upsertSample(written.capture())
        assertEquals("/data/users/user-1/samples/smp-1.jpg", written.firstValue.imagePath)
    }

    @Test
    fun `a frame the device does not hold leaves the path empty rather than inventing one`() =
        runTest {
            setupOnlineSignedIn()
            stubEmptyPulls()
            whenever(sampleRemoteDataSource.fetchOwnSamples("user-1", 0L, 500L))
                .thenReturn(listOf(fakeSample("smp-1", "session-1")))
            whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
            whenever(sampleImageStore.cachedPathOrNull("user-1", "smp-1")).thenReturn(null)

            useCase.invoke()

            // A path to a file that is not there is worse than none: it is indistinguishable
            // from a frame that is really held, and the screen would open blank.
            val written = argumentCaptor<SampleEntity>()
            verify(sampleDao).upsertSample(written.capture())
            assertEquals("", written.firstValue.imagePath)
        }

    @Test
    fun `rows without their frames does not read as a synced device`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(cacheSampleImages.invoke("user-1")).thenReturn(ImageCacheSummary(missing = 3))

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        // Every row type succeeded, and the device still cannot open three samples. Reporting
        // that as All synced is how a medtech finds out in a barangay with no signal.
        assertTrue(FetchType.SAMPLE_IMAGES in summary.failed)
        assertFalse(summary.isComplete)
        verify(fetchOutcomeStore).record(userId = "user-1", complete = false)
    }

    @Test
    fun `a missing frame does not pin the initial-fetch flag shut`() = runTest {
        setupOnlineSignedIn()
        stubEmptyPulls()
        whenever(cacheSampleImages.invoke("user-1")).thenReturn(ImageCacheSummary(missing = 1))

        useCase.invoke()

        // Two different questions. "Did this account's rows arrive" is what clears
        // NOT_YET_SYNCED, and a Storage object that is gone for good must not hold it closed
        // forever - the device really does have every row.
        verify(initialFetchStateStore).markCompleted("user-1")
    }
}
