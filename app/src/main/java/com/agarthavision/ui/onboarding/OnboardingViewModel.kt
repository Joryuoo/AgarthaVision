package com.agarthavision.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferenceRepository: OnboardingPreferenceRepository,
) : ViewModel() {

    fun onCompleteOnboarding() {
        viewModelScope.launch {
            onboardingPreferenceRepository.setHasSeenOnboarding(true)
        }
    }
}
