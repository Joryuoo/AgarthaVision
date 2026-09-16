package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class FetchRemoteDataUseCaseTest {

    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock()
    private val sampleRemoteDataSource: SampleRemoteDataSource = mock()
    private val sessionRemoteDataSource: SessionRemoteDataSource = mock()
    private val reportRemoteDataSource: ReportRemoteDataSource = mock()
    private val sessionDao: SessionDao = mock()
    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val sampleSpeciesFindingDao: SampleSpeciesFindingDao = mock()
    private val reportDao: ReportDao = mock()
    private val initialFetchStateStore: InitialFetchStateStore = mock()

    private val useCase = FetchRemoteDataUseCase(
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        sampleRemoteDataSource = sampleRemoteDataSource,
        sessionRemoteDataSource = sessionRemoteDataSource,
        reportRemoteDataSource = reportRemoteDataSource,
        sessionDao = sessionDao,
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        sampleSpeciesFindingDao = sampleSpeciesFindingDao,
        reportDao = reportDao,
        initialFetchStateStore = initialFetchStateStore,
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
        assertEquals(1, summary.sessionsFetched)
        assertEquals(1, summary.samplesFetched)
        assertEquals(1, summary.reportsFetched)
        verify(initialFetchStateStore).markCompleted("user-1")
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

        verify(sampleDao).insertSample(sample)
    }

    @Test
    fun `E4 - inserts sample when local status is SYNCED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localSynced = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNCED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localSynced)

        useCase.invoke()

        verify(sampleDao).insertSample(sample)
    }

    @Test
    fun `E4 - skips insert when local sample is VERIFIED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localVerified = fakeSample("smp-1", "sess-1", status = SampleStatus.VERIFIED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localVerified)

        useCase.invoke()

        verify(sampleDao, never()).insertSample(any())
    }

    @Test
    fun `E4 - skips insert when local sample is SYNC_FAILED`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localFailed = fakeSample("smp-1", "sess-1", status = SampleStatus.SYNC_FAILED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localFailed)

        useCase.invoke()

        verify(sampleDao, never()).insertSample(any())
    }

    @Test
    fun `E4 - skips insert when local sample is FLAGGED (in-progress work)`() = runTest {
        setupOnlineSignedIn()
        val sample = fakeSample("smp-1", "sess-1")
        setupMinimalFetch(samples = listOf(sample))
        val localFlagged = fakeSample("smp-1", "sess-1", status = SampleStatus.FLAGGED.value)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(localFlagged)

        useCase.invoke()

        verify(sampleDao, never()).insertSample(any())
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

        verify(sessionDao).insertSession(session)
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

        verify(sessionDao).insertSession(session)
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

        verify(sessionDao, never()).insertSession(any())
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
    }

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
        deviceId = "device-1",
        startedAt = 1_000L,
        endedAt = null,
        notes = null,
        supabaseStatus = supabaseStatus,
    )

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
        gpsLatitude = null,
        gpsLongitude = null,
        gpsAccuracy = null,
    )

    private fun fakeReport(id: String, sessionId: String) = ReportEntity(
        reportId = id,
        sessionId = sessionId,
        userId = "user-1",
        generatedAt = 1_000L,
        totalSamples = 0,
        totalEggsConfirmed = 0,
        positiveSpeciesJson = "[]",
        epgPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        supabaseStatus = ReportSyncStatus.SYNCED.value,
        createdAt = 1_000L,
    )
}
