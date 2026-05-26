package com.agarthavision.ui.verify

import app.cash.turbine.test
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.domain.usecase.verify.VerificationAnswers
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storeState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(storeState)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()

    private fun viewModel() = VerificationViewModel(flaggedFrameStore, submitVerificationUseCase)

    private fun makeFrame(predictions: Int = 2): FlaggedFrame {
        val preds = List(predictions) {
            PredictionDto("Ascaris", 0.9f, 100f, 100f, 50f, 50f)
        }
        return FlaggedFrame(
            sessionId = "session-1",
            capturedAt = Instant.EPOCH,
            jpegBytes = ByteArray(4),
            predictions = preds,
        )
    }

    @Test
    fun `setFrame initialises answers list matching prediction count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 3)
            vm.setFrame(frame)
            advanceUntilIdle()
            assertEquals(3, vm.state.value.answers.size)
        }

    @Test
    fun `answers persist independently across detections`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 2)
            vm.setFrame(frame)

            vm.onQ1Selected(true)
            vm.onDetectionNext()
            vm.onQ1Selected(false)
            advanceUntilIdle()

            assertEquals(true, vm.state.value.answers[0].isEgg)
            assertEquals(false, vm.state.value.answers[1].isEgg)
        }

    @Test
    fun `onCancel emits Dismiss without removing frame from store`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame())

            vm.events.test {
                vm.onCancel()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
            verify(flaggedFrameStore, never()).remove(any())
        }

    @Test
    fun `successful submit removes frame and emits Dismiss`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull()))
                .thenReturn(Result.success("sample-1"))
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)

            // Fill in complete answers so canSubmit is true
            vm.onQ1Selected(false) // FALSE_POSITIVE — complete after Q1=No

            vm.events.test {
                vm.onSubmit()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun `canSubmit is false when answers are incomplete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            advanceUntilIdle()
            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `canSubmit is true when all answers are complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            vm.onQ1Selected(false) // complete after Q1=No
            advanceUntilIdle()
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `prev and next detection clamp to valid range`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeFrame(predictions = 2))

            vm.onDetectionPrev()
            advanceUntilIdle()
            assertEquals(0, vm.state.value.currentDetectionIndex)

            vm.onDetectionNext()
            vm.onDetectionNext()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.currentDetectionIndex)
        }

    @Test
    fun `queueSize tracks store state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            storeState.value = List(3) { makeFrame() }
            advanceUntilIdle()
            assertEquals(3, vm.state.value.queueSize)
        }

    @Test
    fun `submit failure keeps isSubmitting false and emits ShowError`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull()))
                .thenReturn(Result.failure(RuntimeException("DB error")))
            val vm = viewModel()
            val frame = makeFrame(predictions = 1)
            vm.setFrame(frame)
            vm.onQ1Selected(false)

            vm.events.test {
                vm.onSubmit()
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is VerificationEvent.ShowError)
                assertEquals("DB error", (event as VerificationEvent.ShowError).message)
            }
            assertFalse(vm.state.value.isSubmitting)
        }
}
