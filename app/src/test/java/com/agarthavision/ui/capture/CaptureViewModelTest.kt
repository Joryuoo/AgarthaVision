package com.agarthavision.ui.capture

import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.capture.CaptureFieldUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val captureFieldUseCase: CaptureFieldUseCase = mock()

    private fun viewModel() = CaptureViewModel(
        sessionManager,
        flaggedFrameStore,
        frameSampler,
        networkMonitor,
        captureFieldUseCase,
    )

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
        )
    }

    private fun makeFrame(): FlaggedFrame = FlaggedFrame(
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    @Test
    fun `Disconnected status latches connection-lost banner`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            networkStatus.value = NetworkMonitor.Status.Disconnected
            advanceUntilIdle()

            assertTrue(vm.state.value.isConnectionLost)
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
    fun `onDetectionToastTap sets verificationTarget`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            val frame = makeFrame()
            vm.onDetectionToastTap(frame)
            advanceUntilIdle()

            assertEquals(frame, vm.state.value.verificationTarget)
            verify(sessionManager, never()).stopSession()
        }

    @Test
    fun `resumeConnection on successful probe clears banner`() =
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

            assertEquals(false, vm.state.value.isConnectionLost)
            assertEquals(false, vm.state.value.isProbingConnection)
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
            assertEquals(false, vm.state.value.isProbingConnection)
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
    fun `onCapture with no active session sets an error and does not call the use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            latestFrameBytes.value = ByteArray(4)

            vm.onCapture()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            verify(captureFieldUseCase, never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `onCapture with no cached frame sets an error and does not call the use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            advanceUntilIdle()

            vm.onCapture()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            verify(captureFieldUseCase, never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `onCapture snapshots the cached frame and routes it through the use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val bytes = ByteArray(4)
            latestFrameBytes.value = bytes
            whenever(captureFieldUseCase.invoke("session-1", bytes))
                .thenReturn(Result.success(FrameSource.MODEL))
            advanceUntilIdle()

            vm.onCapture()
            advanceUntilIdle()

            verify(captureFieldUseCase).invoke("session-1", bytes)
            assertNull(vm.state.value.errorMessage)
            assertEquals(false, vm.state.value.isBusy)
        }
}
