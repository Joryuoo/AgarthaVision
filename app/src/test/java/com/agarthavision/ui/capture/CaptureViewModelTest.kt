package com.agarthavision.ui.capture

import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.camera.FrameSampler
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
    private val latestFrameBytes = MutableStateFlow<ByteArray?>(null)
    private val frameSampler: FrameSampler = mock<FrameSampler>().also {
        whenever(it.latestFrameBytes).thenReturn(latestFrameBytes)
    }
    private val networkMonitor: NetworkMonitor = mock<NetworkMonitor>().also {
        whenever(it.status).thenReturn(networkStatus)
    }

    private fun viewModel() = CaptureViewModel(sessionManager, flaggedFrameStore, frameSampler, networkMonitor)

    private fun makeActiveState(): SessionState.Active {
        val entity = SessionEntity(
            sessionId = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = Instant.EPOCH.toEpochMilli(),
            endedAt = null,
            notes = null,
            label = "Smear 042",
        )
        return SessionState.Active(
            session = entity,
            startedAt = Instant.EPOCH,
            isInferenceRunning = true,
        )
    }

    private fun makeFrame(): FlaggedFrame = FlaggedFrame(
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(PredictionDto("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    @Test
    fun `Disconnected status pauses inference and latches connection-lost banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
            verify(sessionManager).pauseInference()
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `subsequent Connected status does not auto-clear banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Connected
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
        }

    @Test
    fun `onDetectionToastTap pauses inference and sets verificationTarget`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            val frame = makeFrame()
            vm.onDetectionToastTap(frame)
            advanceUntilIdle()

            assertEquals(frame, vm.state.value.verificationTarget)
            verify(sessionManager).pauseInference()
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `resumeConnection on successful probe clears banner and resumes inference`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(networkMonitor.probe()).thenReturn(true)
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()
            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()
            assertTrue(vm.state.value.isConnectionLost)

            vm.resumeConnection()
            advanceUntilIdle()

            assertFalse(vm.state.value.isConnectionLost)
            assertFalse(vm.state.value.isProbingConnection)
            verify(sessionManager).resumeInference()
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `resumeConnection on failed probe keeps banner visible`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(networkMonitor.probe()).thenReturn(false)
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()
            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            vm.resumeConnection()
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
            assertFalse(vm.state.value.isProbingConnection)
            // pauseInference was called on disconnect; resumeInference must NOT fire on failed probe.
            verify(sessionManager, never()).resumeInference()
        }

    @Test
    fun `onVerificationDismissed clears verificationTarget and resumes inference`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onDetectionToastTap(makeFrame())
            advanceUntilIdle()
            assertNotNull(vm.state.value.verificationTarget)

            vm.onVerificationDismissed()
            advanceUntilIdle()

            assertNull(vm.state.value.verificationTarget)
            verify(sessionManager).resumeInference()
        }

    @Test
    fun `onQueueTap pauses inference and sets isQueueOpen`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            vm.onQueueTap()
            advanceUntilIdle()

            assertTrue(vm.state.value.isQueueOpen)
            verify(sessionManager).pauseInference()
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
    fun `onQueueDismiss clears isQueueOpen and resumes inference`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onQueueTap()
            advanceUntilIdle()
            assertTrue(vm.state.value.isQueueOpen)

            vm.onQueueDismiss()
            advanceUntilIdle()

            assertFalse(vm.state.value.isQueueOpen)
            verify(sessionManager).resumeInference()
        }
}
