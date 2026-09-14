package com.agarthavision.ui.verify

import app.cash.turbine.test
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelManualTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storeState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(storeState)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()

    private fun viewModel() = VerificationViewModel(flaggedFrameStore, submitVerificationUseCase)

    /** A frame with an explicit sample id, for assertions that turn on queue position. */
    private fun makeIdentifiedFrame(sampleId: String, predictions: Int = 1) = FlaggedFrame(
        sampleId = sampleId,
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = List(predictions) { Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f) },
    )

    private fun makeManualFrame(sampleId: String) =
        makeIdentifiedFrame(sampleId).copy(source = FrameSource.MANUAL, predictions = emptyList())

    // Manual capture — species checklist

    @Test
    fun `a manual frame opens with empty findings and canSubmit disabled`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Manual captures now use a species checklist — no default finding is seeded.
            // The medtech either checks a species or taps "no detection", so starting with
            // one blank Finding was replaced by starting with an empty list.
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(0, s.findings.size)
            assertFalse(s.noDetectionSelected)
            assertFalse(s.canSubmit)
        }

    @Test
    fun `toggling a species on appends a finding with that species and speciesTouched`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.ASCARIS, true)
            advanceUntilIdle()

            val findings = vm.state.value.findings
            assertEquals(1, findings.size)
            assertEquals(EggSpecies.ASCARIS, findings[0].answers.species)
            assertTrue(findings[0].answers.speciesTouched)
            assertFalse(vm.state.value.noDetectionSelected)
        }

    @Test
    fun `toggling a species off removes its finding`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.HOOKWORM, true)
            vm.onManualSpeciesToggled(EggSpecies.HOOKWORM, false)
            advanceUntilIdle()

            assertEquals(0, vm.state.value.findings.size)
        }

    @Test
    fun `onManualNoDetectionSelected clears findings sets flag and enables submit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.ASCARIS, true)
            vm.onManualNoDetectionSelected()
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.noDetectionSelected)
            assertEquals(0, s.findings.size)
            assertTrue(s.canSubmit)
        }

    @Test
    fun `toggling a species after no-detection clears the noDetectionSelected flag`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualNoDetectionSelected()
            vm.onManualSpeciesToggled(EggSpecies.TRICHURIS, true)
            advanceUntilIdle()

            assertFalse(vm.state.value.noDetectionSelected)
            assertEquals(1, vm.state.value.findings.size)
        }

    @Test
    fun `onManualCountChanged lands a valid count on the correct species`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.HOOKWORM, true)
            vm.onManualCountChanged(EggSpecies.HOOKWORM, "5")
            advanceUntilIdle()

            val answers = vm.state.value.findings
                .first { it.answers.species == EggSpecies.HOOKWORM }.answers
            assertEquals(5, answers.eggCount)
        }

    @Test
    fun `onManualCountChanged with non-numeric text yields null and disables submit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.ASCARIS, true)
            vm.onManualCountChanged(EggSpecies.ASCARIS, "5")
            vm.onManualCountChanged(EggSpecies.ASCARIS, "abc")
            advanceUntilIdle()

            val answers = vm.state.value.findings
                .first { it.answers.species == EggSpecies.ASCARIS }.answers
            assertNull(answers.eggCount)
            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `onManualCountChanged with blank text yields null count`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.ASCARIS, true)
            vm.onManualCountChanged(EggSpecies.ASCARIS, "3")
            vm.onManualCountChanged(EggSpecies.ASCARIS, "")
            advanceUntilIdle()

            assertNull(
                vm.state.value.findings.first { it.answers.species == EggSpecies.ASCARIS }.answers.eggCount,
            )
        }

    @Test
    fun `OTHER species with count but blank name blocks submit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.OTHER, true)
            vm.onManualCountChanged(EggSpecies.OTHER, "2")
            // otherSpeciesText is still blank
            advanceUntilIdle()

            assertFalse(vm.state.value.canSubmit)
        }

    @Test
    fun `OTHER species completes once count and name are both provided`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(makeManualFrame("manual-1"))
            vm.onManualSpeciesToggled(EggSpecies.OTHER, true)
            vm.onManualCountChanged(EggSpecies.OTHER, "2")
            vm.onManualOtherNameChanged("Enterobius")
            advanceUntilIdle()

            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `no-detection submit passes empty findings and null missedEgg to the use case`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success("manual-1"))
            val vm = viewModel()
            val frame = makeManualFrame("manual-1")
            vm.setFrame(frame)
            vm.onManualNoDetectionSelected()
            advanceUntilIdle()

            vm.events.test {
                vm.onSubmit()
                advanceUntilIdle()
                assertEquals(VerificationEvent.Dismiss, awaitItem())
            }

            // Verify findings were empty and missedEgg was null via argument matchers.
            val findingsCaptor = argumentCaptor<List<com.agarthavision.domain.usecase.verify.Finding>>()
            verify(submitVerificationUseCase).invoke(
                frame = any(),
                findings = findingsCaptor.capture(),
                missedEgg = isNull(),
                userNote = anyOrNull(),
            )
            assertEquals(emptyList<com.agarthavision.domain.usecase.verify.Finding>(), findingsCaptor.firstValue)
        }

    @Test
    fun `setFrame with empty prior findings seeds noDetectionSelected true on a manual frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeManualFrame("manual-1")
            val prior = com.agarthavision.domain.usecase.verify.VerificationTarget(
                frame = frame,
                findings = emptyList(),
                missedEgg = null,
                userNote = "",
            )
            vm.setFrame(frame, prior)
            advanceUntilIdle()

            assertTrue(vm.state.value.noDetectionSelected)
        }

    @Test
    fun `setFrame with non-empty prior findings seeds the checklist and leaves noDetectionSelected false`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val frame = makeManualFrame("manual-1")
            val priorFindings = listOf(
                com.agarthavision.domain.usecase.verify.Finding(
                    answers = com.agarthavision.domain.usecase.verify.VerificationAnswers(
                        species = EggSpecies.ASCARIS,
                        speciesTouched = true,
                        eggCount = 3,
                    ),
                ),
            )
            val prior = com.agarthavision.domain.usecase.verify.VerificationTarget(
                frame = frame,
                findings = priorFindings,
                missedEgg = null,
                userNote = "",
            )
            vm.setFrame(frame, prior)
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(s.noDetectionSelected)
            assertEquals(1, s.findings.size)
            assertEquals(EggSpecies.ASCARIS, s.findings[0].answers.species)
        }

}
