package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SyncPatientUseCase
import com.agarthavision.data.supabase.SyncReportUseCase
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.data.supabase.SyncSessionUseCase
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SyncPendingDataUseCase].
 *
 * The one that matters is the ordering test. `sessions.patient_id` references
 * `patients(id)` on the server, so a session pushed before its patient is rejected on its
 * foreign key — and nothing local reproduces that, because Room enforces its own FK
 * against rows that are already there. The failure only appears against real Supabase,
 * which is exactly why the order is pinned here instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPendingDataUseCaseTest {

    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock()
    private val patientDao: PatientDao = mock()
    private val sessionDao: SessionDao = mock()
    private val sampleDao: SampleDao = mock()
    private val reportDao: ReportDao = mock()
    private val syncPatientUseCase: SyncPatientUseCase = mock()
    private val syncSessionUseCase: SyncSessionUseCase = mock()
    private val syncSampleUseCase: SyncSampleUseCase = mock()
    private val syncReportUseCase: SyncReportUseCase = mock()

    private val useCase = SyncPendingDataUseCase(
        authRepository = authRepository,
        connectivityObserver = connectivityObserver,
        patientDao = patientDao,
        sessionDao = sessionDao,
        sampleDao = sampleDao,
        reportDao = reportDao,
        syncPatientUseCase = syncPatientUseCase,
        syncSessionUseCase = syncSessionUseCase,
        syncSampleUseCase = syncSampleUseCase,
        syncReportUseCase = syncReportUseCase,
    )

    @Before
    fun setUp() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn(USER_ID)
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)
        whenever(patientDao.getPatientsPendingSync()).thenReturn(emptyList())
        whenever(sessionDao.getSessionsPendingSync(USER_ID)).thenReturn(emptyList())
        whenever(sampleDao.getSamplesPendingSyncIncludingDeleted(USER_ID)).thenReturn(emptyList())
        whenever(reportDao.getReportsPendingSync(USER_ID)).thenReturn(emptyList())
    }

    // ── FK-safe ordering ──────────────────────────────────────────────────────

    @Test
    fun `a pending patient is pushed before a session that references it`() = runTest {
        whenever(patientDao.getPatientsPendingSync()).thenReturn(listOf(patientEntity()))
        whenever(sessionDao.getSessionsPendingSync(USER_ID)).thenReturn(listOf(sessionEntity()))
        whenever(syncPatientUseCase(PATIENT_ID)).thenReturn(Result.success(Unit))
        whenever(syncSessionUseCase(SESSION_ID)).thenReturn(Result.success(Unit))

        useCase()

        inOrder(syncPatientUseCase, syncSessionUseCase) {
            verify(syncPatientUseCase).invoke(PATIENT_ID)
            verify(syncSessionUseCase).invoke(SESSION_ID)
        }
    }

    @Test
    fun `the summary counts patients that pushed successfully`() = runTest {
        whenever(patientDao.getPatientsPendingSync()).thenReturn(listOf(patientEntity()))
        whenever(syncPatientUseCase(PATIENT_ID)).thenReturn(Result.success(Unit))

        val summary = useCase().getOrNull() as SyncSummary.Ran

        assertEquals(1, summary.patientsSynced)
    }

    @Test
    fun `a patient that fails to push does not abort the pass`() = runTest {
        whenever(patientDao.getPatientsPendingSync()).thenReturn(listOf(patientEntity()))
        whenever(sessionDao.getSessionsPendingSync(USER_ID)).thenReturn(listOf(sessionEntity()))
        whenever(syncPatientUseCase(PATIENT_ID)).thenReturn(Result.failure(IllegalStateException("offline")))
        whenever(syncSessionUseCase(SESSION_ID)).thenReturn(Result.success(Unit))

        val summary = useCase().getOrNull() as SyncSummary.Ran

        assertEquals(0, summary.patientsSynced)
        assertEquals(1, summary.sessionsSynced)
    }

    /**
     * An edited patient is `pending` again after having been `synced`, and
     * `getPatientsPendingSync` returns it a second time. The push must be attempted, not
     * skipped as already-done — the remote write is an upsert precisely so this works.
     */
    @Test
    fun `a patient edited after its first sync is pushed again`() = runTest {
        val edited = patientEntity(lastname = "Cruz-Reyes", supabaseStatus = "pending")
        whenever(patientDao.getPatientsPendingSync()).thenReturn(listOf(edited))
        whenever(syncPatientUseCase(PATIENT_ID)).thenReturn(Result.success(Unit))

        val summary = useCase().getOrNull() as SyncSummary.Ran

        verify(syncPatientUseCase).invoke(PATIENT_ID)
        assertEquals(1, summary.patientsSynced)
    }

    // ── skip conditions ───────────────────────────────────────────────────────

    @Test
    fun `an offline pass skips without touching patients`() = runTest {
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        assertTrue(useCase().getOrNull() is SyncSummary.Skipped)
        verify(patientDao, never()).getPatientsPendingSync()
    }

    @Test
    fun `a signed-out pass skips without touching patients`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn(null)

        assertTrue(useCase().getOrNull() is SyncSummary.Skipped)
        verify(patientDao, never()).getPatientsPendingSync()
    }

    private fun patientEntity(
        lastname: String = "Cruz",
        supabaseStatus: String = "pending",
    ) = PatientEntity(
        patientId = PATIENT_ID,
        lastname = lastname,
        firstname = "Gerald",
        middleName = null,
        sex = "M",
        birthdate = 0L,
        psgcBarangayCode = "0102801001",
        createdBy = USER_ID,
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000,
        supabaseStatus = supabaseStatus,
    )

    private fun sessionEntity() = SessionEntity(
        sessionId = SESSION_ID,
        userId = USER_ID,
        patientId = PATIENT_ID,
        deviceId = "device-1",
        startedAt = 1_700_000_000_000,
    )

    private companion object {
        const val USER_ID = "user-a"
        const val PATIENT_ID = "p-1"
        const val SESSION_ID = "s-1"
    }
}
