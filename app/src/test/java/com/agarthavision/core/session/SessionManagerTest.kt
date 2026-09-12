package com.agarthavision.core.session

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private val manager = SessionManager(
        sessionDao = sessionDao,
        remoteDataSource = remoteDataSource,
        authRepository = authRepository,
        deviceIdProvider = deviceIdProvider,
        activeSessionIdStore = activeSessionIdStore,
    )

    @Test
    fun `startSession without a cached identity creates an unowned pending local row without throwing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn(null)

            val entity = manager.startSession(label = "Smear A")

            val captor = argumentCaptor<SessionEntity>()
            verify(sessionDao).insertSession(captor.capture())
            assertNull(captor.firstValue.userId)
            assertEquals(SessionSyncStatus.PENDING.value, captor.firstValue.supabaseStatus)
            // Unowned sessions are never pushed remotely.
            verify(remoteDataSource, never()).upsertSession(any())
            assertNull(entity.userId)
        }

    @Test
    fun `startSession attributes to the cached identity and marks synced on a successful push`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")

            val entity = manager.startSession(label = "Smear B")

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

            val entity = manager.startSession(label = "Smear C")

            assertEquals(SessionSyncStatus.PENDING.value, entity.supabaseStatus)
            assertEquals("user-1", entity.userId)
        }

    @Test
    fun `clearActive detaches from the session without ending it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            val started = manager.startSession(label = "Smear D")
            assertEquals(started.sessionId, activeSessionIdStore.stored)

            manager.clearActive()

            // Idle, pointer cleared - and crucially the row is untouched, because detaching is
            // not ending. Nothing in the app writes ended_at any more.
            assertTrue(manager.state.value is SessionState.Idle)
            assertNull(activeSessionIdStore.stored)
            verify(remoteDataSource, never()).closeSession(any(), any(), anyOrNull())
        }

    @Test
    fun `restoreActiveSession re-attaches to the stored open session`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The reason this exists: a session outlives the process that created it now, and
            // coming back idle would render the queue empty while the smear is still open.
            val open = sessionEntity(sessionId = "session-1", endedAt = null)
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
    fun `restoreActiveSession refuses a session that was ended before sessions stopped ending`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // ended_at rows are history. resumeSession still rejects them, and the pointer is
            // dropped rather than the app reopening a closed smear.
            val ended = sessionEntity(sessionId = "old", endedAt = 2_000L)
            whenever(sessionDao.getSessionById("old")).thenReturn(ended)
            activeSessionIdStore.stored = "old"

            assertNull(manager.restoreActiveSession())
            assertNull(activeSessionIdStore.stored)
        }

    private fun sessionEntity(sessionId: String, endedAt: Long?) = SessionEntity(
        sessionId = sessionId,
        userId = "user-1",
        deviceId = "device-1",
        startedAt = 1_000L,
        endedAt = endedAt,
        notes = null,
        label = "Smear",
        supabaseStatus = SessionSyncStatus.SYNCED.value,
        claimExempt = false,
    )

}
