package com.agarthavision.ui.capture

import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    private val framesState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val networkStatus = MutableStateFlow<NetworkMonitor.Status>(NetworkMonitor.Status.Connected)

    private val sessionManager: SessionManager = mock<SessionManager>().also {
        whenever(it.state).thenReturn(sessionState)
    }
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(framesState)
    }
    private val networkMonitor: NetworkMonitor = mock<NetworkMonitor>().also {
        whenever(it.status).thenReturn(networkStatus)
    }

    private fun viewModel() = CaptureViewModel(sessionManager, flaggedFrameStore, networkMonitor)

    private fun makeRecordingState(): SessionState.Recording {
        val entity = SessionEntity(
            sessionId = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = Instant.EPOCH.toEpochMilli(),
            endedAt = null,
            notes = null,
        )
        return SessionState.Recording(session = entity, startedAt = Instant.EPOCH)
    }

    private fun makeFrame(): FlaggedFrame = FlaggedFrame(
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(PredictionDto("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    @Test
    fun `Disconnected status while recording stops session and latches connection-lost banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
            verify(sessionManager).stopSession()
        }

    @Test
    fun `subsequent Connected status does not auto-clear banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Connected
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
        }

    @Test
    fun `onDetectionToastTap stops session and sets verificationTarget`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()

            val frame = makeFrame()
            vm.onDetectionToastTap(frame)
            advanceUntilIdle()

            assertEquals(frame, vm.state.value.verificationTarget)
            verify(sessionManager).stopSession()
        }

    @Test
    fun `onDetectionToastTap while idle sets verificationTarget without stopping session`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame()
            vm.onDetectionToastTap(frame)
            advanceUntilIdle()

            assertEquals(frame, vm.state.value.verificationTarget)
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `resumeConnection on successful probe clears banner and restarts session`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(networkMonitor.probe()).thenReturn(true)
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()
            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()
            assertTrue(vm.state.value.isConnectionLost)

            vm.resumeConnection()
            advanceUntilIdle()

            assertFalse(vm.state.value.isConnectionLost)
            assertFalse(vm.state.value.isProbingConnection)
            verify(sessionManager).startSession()
        }

    @Test
    fun `resumeConnection on failed probe keeps banner visible`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(networkMonitor.probe()).thenReturn(false)
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()
            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            vm.resumeConnection()
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
            assertFalse(vm.state.value.isProbingConnection)
            verify(sessionManager, never()).startSession()
        }

    @Test
    fun `onVerificationDismissed clears verificationTarget`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onDetectionToastTap(makeFrame())
            advanceUntilIdle()
            assertNotNull(vm.state.value.verificationTarget)

            vm.onVerificationDismissed()
            advanceUntilIdle()

            assertNull(vm.state.value.verificationTarget)
        }

    @Test
    fun `onQueueTap stops session and sets isQueueOpen`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeRecordingState()
            advanceUntilIdle()

            vm.onQueueTap()
            advanceUntilIdle()

            assertTrue(vm.state.value.isQueueOpen)
            verify(sessionManager).stopSession()
        }

    @Test
    fun `onQueueTap while idle does not stop session`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onQueueTap()
            advanceUntilIdle()

            assertTrue(vm.state.value.isQueueOpen)
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `onQueueItemSelected sets verificationTarget and closes queue`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onQueueTap()
            advanceUntilIdle()
            assertTrue(vm.state.value.isQueueOpen)

            val frame = makeFrame()
            vm.onQueueItemSelected(frame)
            advanceUntilIdle()

            assertEquals(frame, vm.state.value.verificationTarget)
            assertFalse(vm.state.value.isQueueOpen)
        }

    @Test
    fun `onQueueDismiss clears isQueueOpen`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onQueueTap()
            advanceUntilIdle()
            assertTrue(vm.state.value.isQueueOpen)

            vm.onQueueDismiss()
            advanceUntilIdle()

            assertFalse(vm.state.value.isQueueOpen)
        }
}
