package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.FetchOutcomeStore
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
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
    private val patientRemoteDataSource: PatientRemoteDataSource = mock()
    // Answers "the server holds no model output" by default, so the suites that predate the
    // predictions table keep asserting what they were written to assert.
    private val sampleRemoteDataSource: SampleRemoteDataSource = mock {
        onBlocking { fetchPredictions(any()) } doReturn emptyList()
    }
    private val sessionRemoteDataSource: SessionRemoteDataSource = mock()
    private val reportRemoteDataSource: ReportRemoteDataSource = mock()
    private val patientDao: PatientDao = mock()
    private val sessionDao: SessionDao = mock()
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
        verify(patientRemoteDataSource, never()).fetchPatients()
        verify(sessionRemoteDataSource, never()).fetchSessions(any())
    }

    @Test
    fun `returns Skipped when not authenticated`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(false)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(FetchSummary.Skipped, result.getOrThrow())
        verify(patientRemoteDataSource, never()).fetchPatients()
        verify(sessionRemoteDataSource, never()).fetchSessions(any())
    }

    @Test
    fun `returns Skipped when offline`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(FetchSummary.Skipped, result.getOrThrow())
        verify(patientRemoteDataSource, never()).fetchPatients()
        verify(sessionRemoteDataSource, never()).fetchSessions(any())
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `happy path returns Ran with correct counts and calls markCompleted`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        val sample = fakeSample("smp-1", "sess-1")
        val report = fakeReport("rep-1", "sess-1")
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(listOf(sample))
        whenever(sampleRemoteDataSource.fetchDetections(listOf("smp-1"))).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(listOf("smp-1"))).thenReturn(emptyList())
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(null)
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(listOf(report))
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
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(listOf(fakePatient("pat-1")))
        whenever(patientDao.getPatientById("pat-1"))
            .thenReturn(fakePatient("pat-1", PatientSyncStatus.PENDING.value))
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        // That row is a patient the medtech typed in offline. Clobbering it loses them.
        verify(patientDao, never()).upsertPatient(any())
        assertEquals(0, (result.getOrThrow() as FetchSummary.Ran).patientsFetched)
    }

    @Test
    fun `a local SYNC_FAILED patient is not overwritten either`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(listOf(fakePatient("pat-1")))
        whenever(patientDao.getPatientById("pat-1"))
            .thenReturn(fakePatient("pat-1", PatientSyncStatus.SYNC_FAILED.value))
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(patientDao, never()).upsertPatient(any())
    }

    @Test
    fun `an absent patient is inserted along with its link rows`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(listOf(fakePatient("pat-1")))
        whenever(patientDao.getPatientById("pat-1")).thenReturn(null)
        whenever(patientRemoteDataSource.fetchPatientLinks())
            .thenReturn(listOf(PatientUserEntity("pat-1", "user-1", 1_700_000_000_000)))
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        verify(patientDao).upsertPatient(any())
        // Without the link row the patient is on the device and invisible to every read.
        verify(patientDao).linkPatientsToUsers(any())
        assertEquals(1, (result.getOrThrow() as FetchSummary.Ran).patientsFetched)
    }

    @Test
    fun `a failed patient pull leaves markCompleted unset`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatients()).thenThrow(RuntimeException("boom"))
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        // E2: a partial pull must leave the badge honest rather than claim a cache the
        // device does not have. The medtech finds out where there is no signal.
        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `a failed pull names the type that failed and records the pass as incomplete`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatients()).thenThrow(RuntimeException("boom"))
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(emptyList())
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val summary = useCase.invoke().getOrThrow() as FetchSummary.Ran

        assertTrue(summary.isComplete)
        verify(fetchOutcomeStore).record(userId = "user-1", complete = true)
    }

    @Test
    fun `fetches patients before sessions (FK-safe order)`() = runTest {
        setupOnlineSignedIn()
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(emptyList())
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // A session arriving before its patient violates the local FK on sessions.patient_id.
        inOrder(patientRemoteDataSource, sessionRemoteDataSource) {
            verify(patientRemoteDataSource).fetchPatients()
            verify(sessionRemoteDataSource).fetchSessions("user-1")
        }
    }

    // ── Species index refresh (PB-08a) ───────────────────────────────────────

    @Test
    fun `a successful pass folds new species into the offline index`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // The two reference caches have to stay in step: a species that arrived with this
        // pass must be suggestible on the next offline verification.
        verify(speciesSuggestionSeeder).refresh()
    }

    @Test
    fun `a pass whose samples failed still refreshes the species index`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L))
            .thenThrow(RuntimeException("boom"))
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // sessions must be fetched first, then samples
        val order = inOrder(sessionRemoteDataSource, sampleRemoteDataSource, reportRemoteDataSource)
        order.verify(sessionRemoteDataSource).fetchSessions("user-1")
        order.verify(sampleRemoteDataSource).fetchSamples(any(), any(), any())
        order.verify(reportRemoteDataSource).fetchReports("user-1")
    }

    // ── markCompleted only on full success ───────────────────────────────────

    @Test
    fun `markCompleted NOT called when sessions fetch throws`() = runTest {
        setupOnlineSignedIn()
        // thenAnswer (not thenThrow) is required for suspend functions in mockito-kotlin
        whenever(sessionRemoteDataSource.fetchSessions("user-1"))
            .thenAnswer { throw IllegalStateException("network") }
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess) // outer runCatching absorbs per-type failures
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `markCompleted NOT called when samples fetch throws`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L))
            .thenAnswer { throw IllegalStateException("network") }
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `markCompleted NOT called when reports fetch throws`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1"))
            .thenAnswer { throw IllegalStateException("network") }

        val result = useCase.invoke()

        assertTrue(result.isSuccess)
        verify(initialFetchStateStore, never()).markCompleted(any())
    }

    @Test
    fun `other entity types still run even when one throws (partial-failure isolation)`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1"))
            .thenAnswer { throw IllegalStateException("sessions broken") }
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // samples and reports must still be attempted even though sessions threw
        verify(sampleRemoteDataSource).fetchSamples(any(), any(), any())
        verify(reportRemoteDataSource).fetchReports(any())
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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L))
            .thenReturn(listOf(inserted, skipped))
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-inserted")).thenReturn(null)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-skipped"))
            .thenReturn(fakeSample("smp-skipped", "sess-1", status = SampleStatus.VERIFIED.value))
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        // 150 ids / 100 per batch = 2 chunks → 2 calls each
        verify(sampleRemoteDataSource, times(2)).fetchDetections(any())
        verify(sampleRemoteDataSource, times(2)).fetchFindings(any())
    }

    @Test
    fun `chunked - exactly 100 inserted ids produce exactly 1 fetchDetections call`() = runTest {
        setupOnlineSignedIn()
        val page = (1..100).map { fakeSample("smp-$it", "sess-1") }
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, times(1)).fetchDetections(any())
        verify(sampleRemoteDataSource, times(1)).fetchFindings(any())
    }

    @Test
    fun `E4 session guard - inserts when local session is absent`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sessionDao).upsertSession(session)
    }

    @Test
    fun `E4 session guard - inserts when local session has SYNCED status`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session))
        val localSynced = fakeSession("sess-1", supabaseStatus = SessionSyncStatus.SYNCED.value)
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(localSynced)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sessionDao).upsertSession(session)
    }

    @Test
    fun `E4 session guard - skips insert when local session is PENDING`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1")
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session))
        val localPending = fakeSession("sess-1", supabaseStatus = SessionSyncStatus.PENDING.value)
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(localPending)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session1, session2))
        whenever(sessionDao.getSessionById("sess-abcd")).thenReturn(null)
        whenever(sessionDao.getSessionById("sess-efgh")).thenReturn(null)
        // sess-abcd: no collision — pre-check returns 0.
        whenever(sessionDao.countLabelCollisions("patient-1", collidingLabel, "sess-abcd"))
            .thenReturn(0)
        // sess-efgh: collides with the already-inserted sess-abcd — pre-check returns 1.
        // This is the accurate model of what countLabelCollisions returns after sess-abcd lands.
        whenever(sessionDao.countLabelCollisions("patient-1", collidingLabel, "sess-efgh"))
            .thenReturn(1)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
    fun `pullSessions - non-constraint exception from upsertSession is not swallowed`() = runTest {
        setupOnlineSignedIn()
        val session = fakeSession("sess-1").copy(label = "SMEAR-1")
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(listOf(session))
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(null)
        // A non-constraint exception — database is closed, disk full, etc. — must NOT be
        // silently swallowed by the new catch block; it must surface as a sessions failure.
        whenever(sessionDao.upsertSession(session))
            .thenAnswer { throw IllegalStateException("database is closed") }
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        // First page: exactly 500 rows → triggers another request
        val page1 = (1..500).map { fakeSample("smp-$it", "sess-1") }
        // Second page: 0 rows → stops
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(page1)
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 500L, 500L)).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page1.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        val result = useCase.invoke()

        val summary = result.getOrThrow() as FetchSummary.Ran
        assertEquals(500, summary.samplesFetched)
        verify(sampleRemoteDataSource, times(2)).fetchSamples(any(), any(), any())
    }

    @Test
    fun `paginates - short page stops after one request`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        val page = (1..3).map { fakeSample("smp-$it", "sess-1") }
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(page)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        page.forEach { whenever(sampleDao.getSampleByIdIncludingDeleted(it.sampleId)).thenReturn(null) }
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, times(1)).fetchSamples(any(), any(), any())
    }

    @Test
    fun `empty sample page does not call fetchDetections or fetchFindings`() = runTest {
        setupOnlineSignedIn()
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())

        useCase.invoke()

        verify(sampleRemoteDataSource, never()).fetchDetections(any())
        verify(sampleRemoteDataSource, never()).fetchFindings(any())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun setupOnlineSignedIn() {
        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)
        // Patients default to an empty pull. Leaving them unstubbed would make
        // pullPatients throw on a null List, which runCatching swallows into
        // patientsOk = false — and that silently suppresses markCompleted in every
        // test below rather than failing the one that is actually wrong.
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(emptyList())
        whenever(patientRemoteDataSource.fetchPatientLinks()).thenReturn(emptyList())
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
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(samples)
        whenever(sampleRemoteDataSource.fetchDetections(any())).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchFindings(any())).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())
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

    /** Every pull answers with nothing, so a test only stubs the one it is about. */
    private suspend fun stubEmptyPulls() {
        whenever(patientRemoteDataSource.fetchPatients()).thenReturn(emptyList())
        whenever(sessionRemoteDataSource.fetchSessions("user-1")).thenReturn(emptyList())
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L)).thenReturn(emptyList())
        whenever(reportRemoteDataSource.fetchReports("user-1")).thenReturn(emptyList())
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
        whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L))
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
            whenever(sampleRemoteDataSource.fetchSamples("user-1", 0L, 500L))
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
