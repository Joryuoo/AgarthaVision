package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Verifying a frame captured while the inference container was unreachable.
 *
 * **This replaces the manual species checklist**, which was a second, parallel way of saying
 * what is in a field, reached only on this one kind of frame. A No-Model-Output frame is now an
 * ordinary frame whose Model Output section says the container never answered, verified through
 * the same always-present Add Egg section every other frame uses. One path, so there is one
 * place for it to be wrong.
 *
 * What this suite pins is that the path actually works end to end — because it is the only path
 * by which such a frame can be verified at all, and losing it would silently strand every field
 * a medtech captured during an outage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationNoModelOutputTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frames = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(frames)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()

    // Stubbed to answer with nothing: these suites are not about suggestions, but the free-text
    // handlers query the index now and an unstubbed mock would answer null.
    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase = mock {
        onBlocking { invoke(any()) } doReturn Result.success(emptyList())
    }

    private fun viewModel() = VerificationViewModel(
        flaggedFrameStore,
        submitVerificationUseCase,
        searchSpeciesSuggestions,
    )

    private fun noModelOutputFrame() = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = emptyList(),
        source = FrameSource.MANUAL,
    )

    @Test
    fun `it opens with nothing to answer, because there is no box to ask about`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())
            advanceUntilIdle()

            assertTrue(vm.state.value.findings.isEmpty())
            assertTrue(vm.state.value.isManual)
        }

    /**
     * Submitting an empty list *is* the "I saw nothing" assertion.
     *
     * The checklist this replaced made the medtech tap a no-detection row first. That tap asked
     * them to restate the absence of work, and blocked submit until they did.
     */
    @Test
    fun `a field the medtech saw nothing in submits as it stands`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())
            advanceUntilIdle()

            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `an added species opens holding one egg, so a name is all it needs`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())

            vm.onAddSpecies()
            advanceUntilIdle()
            // A species is still required: an egg nobody named is not a finding.
            assertEquals(1, vm.state.value.findings[0].answers.fieldTotal)
            assertFalse(vm.state.value.canSubmit)

            vm.onAddedSpeciesSelected(0, EggSpecies.ASCARIS)
            advanceUntilIdle()

            assertTrue(vm.state.value.canSubmit)
        }

    /**
     * An added egg with **no bounding box** is a complete finding.
     *
     * `detections.bbox_*` is nullable precisely for this (`0007_detection_bbox_nullable.sql`);
     * drawing a box is optional, and may be deferred to the Sample Data Screen entirely.
     */
    @Test
    fun `an added species needs no box behind it to be complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.HOOKWORM)
            advanceUntilIdle()

            val finding = vm.state.value.findings[0]
            assertNull("No model output means no box.", finding.prediction)
            assertTrue(finding.isComplete)
        }

    @Test
    fun `several species in one field are several added cards`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())

            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.ASCARIS)
            vm.onFieldTotalChanged(0, "3")
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(1, EggSpecies.TRICHURIS)
            advanceUntilIdle()

            val findings = vm.state.value.findings
            assertEquals(2, findings.size)
            assertEquals(3, findings[0].answers.fieldTotal)
            assertEquals(1, findings[1].answers.fieldTotal)
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun `OTHER still needs its free-text name before the row is complete`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(noModelOutputFrame())
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.OTHER)
            advanceUntilIdle()
            assertFalse("OTHER carries no canonical class of its own.", vm.state.value.canSubmit)

            vm.onAddedOtherSpeciesChanged(0, "Schistosoma")
            advanceUntilIdle()

            assertTrue(vm.state.value.canSubmit)
        }

    /**
     * No model output means no model claim, so there is nothing for it to have missed — null
     * rather than false, because false would assert something about a model that never ran.
     */
    @Test
    fun `submitting passes a null missed-egg answer, whatever the medtech added`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(submitVerificationUseCase.invoke(any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success("sample-1"))
            val frame = noModelOutputFrame()
            frames.value = listOf(frame)
            val vm = viewModel()
            vm.setFrame(frame)
            vm.onAddSpecies()
            vm.onAddedSpeciesSelected(0, EggSpecies.ASCARIS)
            advanceUntilIdle()

            vm.onSubmit()
            advanceUntilIdle()

            val findingsCaptor = argumentCaptor<List<Finding>>()
            verify(submitVerificationUseCase).invoke(
                frame = any(),
                findings = findingsCaptor.capture(),
                missedEgg = isNull(),
                userNote = anyOrNull(),
            )
            assertEquals(1, findingsCaptor.firstValue.size)
            assertTrue(findingsCaptor.firstValue[0].answers.speciesTouched)
        }
}
