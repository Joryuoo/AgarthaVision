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
import com.agarthavision.domain.usecase.capture.CaptureOutcome
import com.agarthavision.util.MainDispatcherRule
import app.cash.turbine.test
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
            patientId = "patient-1",
            deviceId = "device-1",
            startedAt = Instant.EPOCH.toEpochMilli(),
            label = "Smear 042",
        )
        return SessionState.Active(
            session = entity,
            startedAt = Instant.EPOCH,
        )
    }

    private fun makeFrame(sampleId: String = "sample-1", capturedAt: Instant = Instant.EPOCH) =
        FlaggedFrame(
            sampleId = sampleId,
            sessionId = "session-1",
            capturedAt = capturedAt,
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
    fun `onCapturedFrameToastTap opens the sample the confirmation names`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val older = makeFrame(sampleId = "sample-older", capturedAt = Instant.EPOCH)
            val tapped = makeFrame(sampleId = "sample-tapped", capturedAt = Instant.ofEpochSecond(30))
            framesState.value = listOf(tapped, older)
            advanceUntilIdle()

            vm.onCapturedFrameToastTap("sample-older")
            advanceUntilIdle()

            // By id, not by position: the head of the queue is not what the medtech tapped.
            assertEquals(older, vm.state.value.verificationTarget)
        }

    /**
     * The confirmation outlives the row it is about. A sample verified or deleted from
     * elsewhere in those two seconds has nothing left to open, and a lookup that misses must
     * do nothing rather than fall back to whatever row is nearest.
     */
    @Test
    fun `onCapturedFrameToastTap does nothing for a sample that has left the queue`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            framesState.value = listOf(makeFrame(sampleId = "sample-still-here"))
            advanceUntilIdle()

            vm.onCapturedFrameToastTap("sample-gone")
            advanceUntilIdle()

            assertNull(vm.state.value.verificationTarget)
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
            framesState.value = listOf(makeFrame(sampleId = "sample-1"))
            advanceUntilIdle()
            vm.onCapturedFrameToastTap("sample-1")
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
                .thenReturn(Result.success(CaptureOutcome("sample-1", FrameSource.MODEL)))
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
                .thenReturn(Result.success(CaptureOutcome("sample-1", FrameSource.MODEL)))
            advanceUntilIdle()

            now += MAX_FRAME_AGE_MS

            vm.onCapture()
            advanceUntilIdle()

            verify(captureFieldUseCase).invoke("session-1", bytes)
            assertNull(vm.state.value.errorMessage)
        }

    /**
     * Every successful tap is confirmed, and the confirmation names the row that tap wrote.
     *
     * The id matters more than the message: it is what lets the medtech open the frame they
     * just took rather than whatever is currently newest.
     */
    @Test
    fun `onCapture emits one FrameCaptured event carrying the row it wrote`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val bytes = publishFrame()
            whenever(captureFieldUseCase.invoke("session-1", bytes))
                .thenReturn(Result.success(CaptureOutcome("sample-7", FrameSource.MODEL)))
            advanceUntilIdle()

            vm.events.test {
                vm.onCapture()
                advanceUntilIdle()

                assertEquals(
                    CaptureEvent.FrameCaptured(CaptureOutcome("sample-7", FrameSource.MODEL)),
                    awaitItem(),
                )
                expectNoEvents()
            }
        }

    /**
     * A field the inference container never saw is still a capture, and is confirmed as one.
     *
     * The medtech gets the same reassurance the tap landed; only the wording differs, so ten
     * captures taken with the container down do not read as ten ordinary ones.
     */
    @Test
    fun `a capture taken with the container unreachable is still confirmed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val bytes = publishFrame()
            whenever(captureFieldUseCase.invoke("session-1", bytes))
                .thenReturn(Result.success(CaptureOutcome("sample-8", FrameSource.MANUAL)))
            advanceUntilIdle()

            vm.events.test {
                vm.onCapture()
                advanceUntilIdle()

                val event = awaitItem() as CaptureEvent.FrameCaptured
                assertEquals(FrameSource.MANUAL, event.outcome.source)
            }
        }

    /**
     * The regression this event exists for.
     *
     * The confirmation used to be derived from the head of the flagged queue, which is a live
     * Room query: verifying the newest sample promotes an older one, and the screen announced
     * "Frame captured" for a frame captured minutes earlier. Nobody tapped the shutter here, so
     * nothing may be confirmed.
     */
    @Test
    fun `a queue that reshuffles on its own confirms nothing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            sessionState.value = makeActiveState()
            val older = makeFrame(sampleId = "sample-older", capturedAt = Instant.EPOCH)
            val newer = makeFrame(sampleId = "sample-newer", capturedAt = Instant.ofEpochSecond(30))
            advanceUntilIdle()

            vm.events.test {
                // Entering the screen with a queue already populated.
                framesState.value = listOf(newer, older)
                advanceUntilIdle()

                // The newest is verified, so the older one becomes the head.
                framesState.value = listOf(older)
                advanceUntilIdle()

                expectNoEvents()
            }
        }

    private companion object {
        /** Mirrors `CaptureViewModel.MAX_FRAME_AGE_MS`, which is private to that class. */
        private const val MAX_FRAME_AGE_MS = 1_000L

        /** Comfortably past the window — the gap a real session change leaves. */
        private const val STALE_FRAME_AGE_MS = 5_000L
    }
}
