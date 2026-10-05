package com.agarthavision.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferenceRepository: OnboardingPreferenceRepository,
) : ViewModel() {

    private var completing = false

    /** Saves the flag first, then calls [onSaved]; a failed write is logged and navigation continues. */
    @Suppress("TooGenericExceptionCaught")
    fun onCompleteOnboarding(onSaved: () -> Unit) {
        if (completing) return
        completing = true
        viewModelScope.launch {
            try {
                onboardingPreferenceRepository.setHasSeenOnboarding(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save onboarding completion", e)
            }
            withContext(Dispatchers.Main) { onSaved() }
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
