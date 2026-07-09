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

    private val manager = SessionManager(
        sessionDao = sessionDao,
        remoteDataSource = remoteDataSource,
        authRepository = authRepository,
        deviceIdProvider = deviceIdProvider,
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
    fun `stopSession ends the session locally even when the remote close fails`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
            manager.startSession(label = "Smear D")
            @Suppress("TooGenericExceptionThrown")
            whenever(remoteDataSource.closeSession(any(), any(), anyOrNull()))
                .thenAnswer { throw RuntimeException("offline") }

            manager.stopSession()

            // Local row was updated with an ended timestamp, and status fell back to pending.
            val captor = argumentCaptor<SessionEntity>()
            verify(sessionDao, org.mockito.kotlin.atLeastOnce()).updateSession(captor.capture())
            assertEquals("Smear D", captor.lastValue.label)
            assert(captor.lastValue.endedAt != null)
            verify(sessionDao).updateSupabaseStatus(any(), eq(SessionSyncStatus.PENDING.value))
            assertEquals(SessionState.Idle, manager.state.value)
        }
}
