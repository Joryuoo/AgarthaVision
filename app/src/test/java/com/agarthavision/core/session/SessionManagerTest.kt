package com.agarthavision.core.session

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.agarthavision.domain.sync.RecordingSyncScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionDao: SessionDao = mock()
    private val remoteDataSource: SessionRemoteDataSource = mock()
    private val authRepository: AuthRepository = mock()
    private val deviceIdProvider: DeviceIdProvider = mock()

    /** In-memory stand-in for the DataStore-backed pointer. */
    private val activeSessionIdStore = object : ActiveSessionIdStore {
        var stored: String? = null
        override suspend fun read(): String? = stored
        override suspend fun write(sessionId: String?) {
            stored = sessionId
        }
    }

    private val syncScheduler = RecordingSyncScheduler()

    private val manager = SessionManager(
        sessionDao = sessionDao,
        remoteDataSource = remoteDataSource,
        authRepository = authRepository,
        deviceIdProvider = deviceIdProvider,
        activeSessionIdStore = activeSessionIdStore,
        syncScheduler = syncScheduler,
    )

    @Test
    fun `startSession refuses to create a session with no cached identity`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn(null)

            // Login is mandatory on first run, so this is unreachable through the UI. It
            // throws rather than writing an unowned row because a session with no owner
            // cannot be pushed, and creating one silently strands the smear on the device.
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { manager.startSession(label = "Smear A", patientId = "patient-1") }
            }
            verify(sessionDao, never()).upsertSession(any())
            verify(remoteDataSource, never()).upsertSession(any())
        }

    @Test
    fun `startSession attributes to the cached identity and marks synced on a successful push`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")

            val entity = manager.startSession(label = "Smear B", patientId = "patient-1")

            verify(remoteDataSource).upsertSession(any())
            assertEquals("user-1", entity.userId)
            assertEquals(SessionSyncStatus.SYNCED.value, entity.supabaseStatus)
        }

    @Test
    fun `startSession keeps the session pending and does not throw when the remote push fails`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            whenever(remoteDataSource.upsertSession(any())).thenThrow(RuntimeException("offline"))

            val entity = manager.startSession(label = "Smear C", patientId = "patient-1")

            assertEquals(SessionSyncStatus.PENDING.value, entity.supabaseStatus)
            assertEquals("user-1", entity.userId)
        }

    @Test
    fun `clearActive detaches from the session without ending it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            val started = manager.startSession(label = "Smear D", patientId = "patient-1")
            assertEquals(started.sessionId, activeSessionIdStore.stored)

            manager.clearActive()

            // Idle, pointer cleared - and crucially the row is untouched, because detaching is
            // not ending. Nothing in the app writes ended_at any more.
            assertTrue(manager.state.value is SessionState.Idle)
            assertNull(activeSessionIdStore.stored)
        }

    @Test
    fun `restoreActiveSession re-attaches to the stored open session`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The reason this exists: a session outlives the process that created it now, and
            // coming back idle would render the queue empty while the smear is still open.
            val open = sessionEntity(sessionId = "session-1")
            whenever(sessionDao.getSessionById("session-1")).thenReturn(open)
            activeSessionIdStore.stored = "session-1"

            val restored = manager.restoreActiveSession()

            assertEquals("session-1", restored?.sessionId)
            assertTrue(manager.state.value is SessionState.Active)
        }

    @Test
    fun `restoreActiveSession clears a pointer it cannot resolve`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // A stale pointer must never stop the app launching.
            whenever(sessionDao.getSessionById("gone")).thenReturn(null)
            activeSessionIdStore.stored = "gone"

            assertNull(manager.restoreActiveSession())
            assertNull(activeSessionIdStore.stored)
            assertTrue(manager.state.value is SessionState.Idle)
        }

    @Test
    fun `restoreActiveSession is a no-op when the state is already Active`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Guards against the race introduced by calling restore from AgarthaVisionApp.onCreate
            // off the main thread while a user-initiated startSession/resumeSession can run
            // concurrently: a session already active must never be clobbered by a stale restore.
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            val started = manager.startSession(label = "Smear E", patientId = "patient-1")

            // A different stored id than the one already active.
            val other = sessionEntity(sessionId = "session-other")
            whenever(sessionDao.getSessionById("session-other")).thenReturn(other)
            activeSessionIdStore.stored = "session-other"

            val restored = manager.restoreActiveSession()

            assertNull(restored)
            val state = manager.state.value
            assertTrue(state is SessionState.Active)
            assertEquals(started.sessionId, (state as SessionState.Active).session.sessionId)
        }

    @Test
    fun `restoreActiveSession loses a genuine mid-flight race to a concurrent startSession`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Unlike the "already Active" test above (where startSession finishes before
            // restore even begins), this suspends restore's dao lookup mid-flight so a real
            // startSession can interleave and win the race the CAS guard exists to handle.
            val storedEntity = sessionEntity(sessionId = "stored-session")
            val gate = CompletableDeferred<Unit>()
            val racyDao = object : SessionDao by sessionDao {
                override suspend fun getSessionById(sessionId: String): SessionEntity? {
                    gate.await()
                    return sessionDao.getSessionById(sessionId)
                }
            }
            whenever(sessionDao.getSessionById("stored-session")).thenReturn(storedEntity)
            activeSessionIdStore.stored = "stored-session"

            val racyManager = SessionManager(
                sessionDao = racyDao,
                remoteDataSource = remoteDataSource,
                authRepository = authRepository,
                deviceIdProvider = deviceIdProvider,
                activeSessionIdStore = activeSessionIdStore,
                syncScheduler = syncScheduler,
            )

            var restoreResult: SessionEntity? = null
            val restoreJob = launch { restoreResult = racyManager.restoreActiveSession() }
            // Runs restoreActiveSession up to its suspension point inside getSessionById,
            // past the initial Idle check but before it has read the entity.
            runCurrent()

            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            val started = racyManager.startSession(label = "Smear F", patientId = "patient-1")

            // Let the stalled restore resume: it now finds state is no longer Idle.
            gate.complete(Unit)
            restoreJob.join()

            assertNull(restoreResult)
            val state = racyManager.state.value
            assertTrue(state is SessionState.Active)
            assertEquals(started.sessionId, (state as SessionState.Active).session.sessionId)
        }

    private fun sessionEntity(sessionId: String) = SessionEntity(
        sessionId = sessionId,
        userId = "user-1",
        patientId = "patient-1",
        deviceId = "device-1",
        startedAt = 1_000L,
        label = "Smear",
        supabaseStatus = SessionSyncStatus.SYNCED.value,
    )

}
