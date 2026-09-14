package com.agarthavision.ui.capture

import com.agarthavision.core.connectivity.NetworkMonitor
import com.agarthavision.core.camera.CachedFrame
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.util.ElapsedClock
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
    private val latestFrame = MutableStateFlow<CachedFrame?>(null)
    private val frameSampler: FrameSampler = mock<FrameSampler>().also {
        whenever(it.latestFrame).thenReturn(latestFrame)
    }

    /**
     * Settable stand-in for `SystemClock.elapsedRealtime()`. Moving [now] by hand is what
     * lets the staleness tests below run on the plain JVM instead of on Robolectric.
     */
    private var now = 0L
    private val clock = ElapsedClock { now }
    private val networkMonitor: NetworkMonitor = mock<NetworkMonitor>().also {
        whenever(it.status).thenReturn(networkStatus)
    }
    private val captureFieldUseCase: CaptureFieldUseCase = mock()

    /** Publishes a frame stamped at the current [now]. */
    private fun publishFrame(bytes: ByteArray = ByteArray(4)): ByteArray {
        latestFrame.value = CachedFrame(bytes, now)
        return bytes
    }

    private fun viewModel() = CaptureViewModel(
        sessionManager,
        flaggedFrameStore,
        frameSampler,
        networkMonitor,
        captureFieldUseCase,
        clock,
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
            publishFrame()

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
            val bytes = publishFrame()
            whenever(captureFieldUseCase.invoke("session-1", bytes))
                .thenReturn(Result.success(FrameSource.MODEL))
            advanceUntilIdle()

            vm.onCapture()
            advanceUntilIdle()

            verify(captureFieldUseCase).invoke("session-1", bytes)
            assertNull(vm.state.value.errorMessage)
            assertEquals(false, vm.state.value.isBusy)
        }

    /**
     * The regression this guard exists for. `FrameSampler` is process-scoped, so the last
     * frame of the previous session is still sitting in the cache when the next one starts.
     * A tap landing before the analyzer delivers a frame for the new binding must be
     * refused — recording it would file one patient's image under another's session, and
     * C8 makes that permanent once it is verified.
     */
    @Test
    fun `onCapture rejects a frame older than the freshness window`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            publishFrame()
            advanceUntilIdle()

            now += STALE_FRAME_AGE_MS

            vm.onCapture()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            verify(captureFieldUseCase, never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    /**
     * Pins the comparison to `>` rather than `>=`: a frame sitting exactly on the boundary
     * is still live. Without this, tightening the operator would pass silently.
     */
    @Test
    fun `onCapture accepts a frame exactly at the freshness boundary`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val bytes = publishFrame()
            whenever(captureFieldUseCase.invoke("session-1", bytes))
                .thenReturn(Result.success(FrameSource.MODEL))
            advanceUntilIdle()

            now += MAX_FRAME_AGE_MS

            vm.onCapture()
            advanceUntilIdle()

            verify(captureFieldUseCase).invoke("session-1", bytes)
            assertNull(vm.state.value.errorMessage)
        }

    private companion object {
        /** Mirrors `CaptureViewModel.MAX_FRAME_AGE_MS`, which is private to that class. */
        private const val MAX_FRAME_AGE_MS = 1_000L

        /** Comfortably past the window — the gap a real session change leaves. */
        private const val STALE_FRAME_AGE_MS = 5_000L
    }
}
