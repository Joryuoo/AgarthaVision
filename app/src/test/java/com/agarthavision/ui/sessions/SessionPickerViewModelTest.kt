package com.agarthavision.ui.sessions

import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionsFlow = MutableStateFlow<List<SessionEntity>>(emptyList())
    private val sessionDao: SessionDao = mock<SessionDao>().also {
        whenever(it.observeActiveAndRecent(any(), any())).thenReturn(sessionsFlow)
    }
    private val sessionManager: SessionManager = mock()
    private val sessionRemoteDataSource: SessionRemoteDataSource = mock<SessionRemoteDataSource>().also {
        whenever(it.currentUserId()).thenReturn("user-1")
    }

    private fun viewModel() = SessionPickerViewModel(sessionDao, sessionManager, sessionRemoteDataSource)

    @Test
    fun `state mirrors active and recent sessions`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        // state is a WhileSubscribed flow — subscribe via Turbine to drive collection.
        vm.state.test {
            // initial emission has isLoading=true; drain until we see isLoading=false
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertFalse(snapshot.isLoading)

            sessionsFlow.value = listOf(makeSession("session-1"))
            val withSession = awaitItem()
            assertEquals(1, withSession.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCreateSession emits navigate event`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        whenever(sessionManager.startSession(any(), anyOrNull())).thenReturn(makeSession("session-1"))

        vm.events.test {
            vm.onCreateSession("Smear 1", null)
            advanceUntilIdle()
            val event = awaitItem() as SessionPickerEvent.NavigateToCapture
            assertEquals("session-1", event.sessionId)
        }
    }

    @Test
    fun `onCreateSession rejects blank labels`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.state.test {
            // initial emission(s)
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            vm.onCreateSession("   ", null)
            val withError = awaitItem()
            assertTrue(withError.errorMessage?.contains("Label") == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeSession(id: String): SessionEntity = SessionEntity(
        sessionId = id,
        userId = "user-1",
        deviceId = "device-1",
        startedAt = Instant.EPOCH.toEpochMilli(),
        endedAt = null,
        notes = null,
        label = "Smear 1",
    )
}
