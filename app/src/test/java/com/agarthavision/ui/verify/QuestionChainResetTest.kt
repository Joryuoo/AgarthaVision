package com.agarthavision.ui.verify

import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * What unticking and re-ticking a question leaves behind.
 *
 * The screen draws every question as a checkbox, and a checkbox has two states. So an answer that
 * reaches the screen as null draws exactly like "No" — unticked — while hiding the correction an
 * unticked question carries. That is the bug these pin: re-ticking Q1 used to leave Q2 and Q3 null,
 * and the medtech had to tick and untick each of them just to reach the redraw and the picker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionChainResetTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flaggedFrameStore: FlaggedFrameStore = mock<FlaggedFrameStore>().also {
        whenever(it.state).thenReturn(MutableStateFlow(emptyList()))
    }

    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase = mock {
        onBlocking { invoke(any()) } doReturn Result.success(emptyList())
    }

    private fun viewModel() = VerificationViewModel(
        flaggedFrameStore,
        mock<SubmitVerificationUseCase>(),
        searchSpeciesSuggestions,
    )

    private fun frame() = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(4),
        predictions = listOf(Prediction("Ascaris", 0.9f, 100f, 100f, 50f, 50f)),
    )

    @Test
    fun `re-ticking Q1 brings Q2 and Q3 back unticked, not unanswered`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onQ1Selected(false)
            vm.onQ1Selected(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(true, answers.isEgg)
            assertEquals("Unticked, so the redraw is offered.", false, answers.isBoxCorrect)
            assertEquals("Unticked, so the picker is offered.", false, answers.speciesConfirmed)
            assertNull("The model's species was disowned with Q1.", answers.species)
        }

    @Test
    fun `unticking Q1 leaves Q2 unanswered, since it is not asked without an egg`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onQ1Selected(false)
            advanceUntilIdle()

            assertNull(vm.state.value.findings[0].answers.isBoxCorrect)
        }

    @Test
    fun `unticking Q2 keeps the model's species confirmed under Q3`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())

            vm.onQ2Selected(false)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(false, answers.isBoxCorrect)
            assertEquals(true, answers.speciesConfirmed)
            assertEquals(EggSpecies.ASCARIS, answers.species)
        }

    @Test
    fun `toggling Q2 keeps a species picked from the dropdown`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.setFrame(frame())
            vm.onSpeciesConfirmed(false)
            vm.onSpeciesSelected(EggSpecies.TRICHURIS)

            vm.onQ2Selected(false)
            vm.onQ2Selected(true)
            advanceUntilIdle()

            val answers = vm.state.value.findings[0].answers
            assertEquals(true, answers.isBoxCorrect)
            assertEquals(false, answers.speciesConfirmed)
            assertEquals(EggSpecies.TRICHURIS, answers.species)
            assertEquals(true, answers.speciesTouched)
        }
}
