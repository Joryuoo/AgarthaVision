package com.agarthavision.ui.onboarding

import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onboardingPreferenceRepository: OnboardingPreferenceRepository = mock()

    @Test
    fun `onCompleteOnboarding marks onboarding as seen in preference repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = OnboardingViewModel(onboardingPreferenceRepository)

            viewModel.onCompleteOnboarding {}
            advanceUntilIdle()

            verify(onboardingPreferenceRepository).setHasSeenOnboarding(true)
        }

    @Test
    fun `callback fires only after the write`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            var written = false
            var writtenWhenCalledBack: Boolean? = null
            whenever(onboardingPreferenceRepository.setHasSeenOnboarding(true)).doSuspendableAnswer {
                written = true
                Unit
            }
            val viewModel = OnboardingViewModel(onboardingPreferenceRepository)

            viewModel.onCompleteOnboarding { writtenWhenCalledBack = written }
            advanceUntilIdle()

            assertEquals(true, writtenWhenCalledBack)
        }

    @Test
    fun `double call writes once and calls back once`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            var calls = 0
            val viewModel = OnboardingViewModel(onboardingPreferenceRepository)

            viewModel.onCompleteOnboarding { calls++ }
            viewModel.onCompleteOnboarding { calls++ }
            advanceUntilIdle()

            verify(onboardingPreferenceRepository, times(1)).setHasSeenOnboarding(true)
            assertEquals(1, calls)
        }

    @Test
    fun `failed write still calls back`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(onboardingPreferenceRepository.setHasSeenOnboarding(true))
                .doThrow(RuntimeException("disk full"))
            var calls = 0
            val viewModel = OnboardingViewModel(onboardingPreferenceRepository)

            viewModel.onCompleteOnboarding { calls++ }
            advanceUntilIdle()

            assertEquals(1, calls)
        }
}
