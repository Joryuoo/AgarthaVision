package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * The "Other species" field and the offline suggestion index (86d4byedj).
 *
 * `SearchSpeciesSuggestionsUseCase` shipped with PB-08b and was never called: it had zero
 * callers in `app/src/main` for the whole Phase 3 stack, and a passing unit test of its own,
 * which is how an autocomplete nobody could see survived every gate. These tests assert the
 * consumer exists, not the query - the use case's own suite covers that.
 *
 * Free text is the only path by which a species outside `EggSpecies` reaches
 * `detections.expert_class` and `sample_species_findings.species`, and those tables double as
 * the retraining corpus. A misspelling there is permanent and ungroupable, so a suggestion that
 * never renders prevents nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesSuggestionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storeState = MutableStateFlow<List<FlaggedFrame>>(emptyList())
    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(storeState)
    }
    private val submitVerificationUseCase: SubmitVerificationUseCase = mock()
    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase = mock()

    private fun viewModel() = VerificationViewModel(
        flaggedFrameStore,
        submitVerificationUseCase,
        searchSpeciesSuggestions,
    )

    private fun frame() = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    private suspend fun answering(vararg names: String) {
        whenever(searchSpeciesSuggestions(any())).thenReturn(Result.success(names.toList()))
    }

    @Test
    fun `typing in Other species offers what this device has already recorded`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering("Fasciola hepatica", "Fasciolopsis buski")
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onOtherSpeciesChanged("fas")
            advanceUntilIdle()

            assertEquals(
                listOf("Fasciola hepatica", "Fasciolopsis buski"),
                vm.state.value.suggestionsFor(SuggestionTarget.CurrentDetection, "fas"),
            )
        }

    @Test
    fun `a burst of typing costs one lookup, not one per keystroke`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering("Fasciola hepatica")
            val vm = viewModel()
            vm.setFrame(frame())

            // No clock advance between them: each keystroke cancels the pending lookup, so only
            // the last one ever reaches the index.
            vm.onOtherSpeciesChanged("f")
            vm.onOtherSpeciesChanged("fa")
            vm.onOtherSpeciesChanged("fas")
            vm.onOtherSpeciesChanged("fasc")
            advanceUntilIdle()

            verify(searchSpeciesSuggestions, times(1)).invoke("fasc")
        }

    @Test
    fun `a list fetched for an added species does not render under the current detection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering("Fasciola hepatica")
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onAddSpecies()
            advanceUntilIdle()
            val added = vm.state.value.findings.lastIndex

            vm.onAddedOtherSpeciesChanged(added, "fas")
            advanceUntilIdle()

            // The screen has two free-text fields and one list. Rendering it under the wrong one
            // invites a tap that writes one row's species into another.
            assertEquals(
                listOf("Fasciola hepatica"),
                vm.state.value.suggestionsFor(SuggestionTarget.AddedFinding(added), "fas"),
            )
            assertEquals(
                emptyList<String>(),
                vm.state.value.suggestionsFor(SuggestionTarget.CurrentDetection, "fas"),
            )
        }

    @Test
    fun `a list does not outlive the text that produced it`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering("Fasciola hepatica")
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onOtherSpeciesChanged("fas")
            advanceUntilIdle()

            // One more keystroke, and the names on screen answer a question that is no longer
            // being asked. They must not be offered against the new text.
            assertEquals(
                emptyList<String>(),
                vm.state.value.suggestionsFor(SuggestionTarget.CurrentDetection, "fasx"),
            )
        }

    @Test
    fun `a lookup that fails offers nothing rather than the previous names`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering("Fasciola hepatica")
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onOtherSpeciesChanged("fas")
            advanceUntilIdle()

            whenever(searchSpeciesSuggestions(any()))
                .thenReturn(Result.failure(IllegalStateException("index unreadable")))
            vm.onOtherSpeciesChanged("fasc")
            advanceUntilIdle()

            // The index is a local table read. A failure is not a transient offline blip worth
            // riding out - it means the device cannot stand behind what is on screen.
            assertEquals(
                emptyList<String>(),
                vm.state.value.suggestionsFor(SuggestionTarget.CurrentDetection, "fasc"),
            )
        }

    @Test
    fun `typing a name the index has never seen still stands`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            answering()
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onOtherSpeciesChanged("Capillaria philippinensis")
            advanceUntilIdle()

            // The whole point of the field. A suggestion is a convenience, never a vocabulary
            // the corpus is limited to - a species new to this device has to be enterable.
            assertEquals(
                "Capillaria philippinensis",
                vm.state.value.findings[0].answers.otherSpeciesText,
            )
        }
}
