package com.agarthavision.ui.onboarding

import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onboardingPreferenceRepository: OnboardingPreferenceRepository = mock()

    @Test
    fun `onCompleteOnboarding marks onboarding as seen in preference repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel = OnboardingViewModel(onboardingPreferenceRepository)

            viewModel.onCompleteOnboarding()
            advanceUntilIdle()

            verify(onboardingPreferenceRepository).setHasSeenOnboarding(true)
        }
}
