package com.agarthavision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.BiometricLockRepository
import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import com.agarthavision.domain.usecase.auth.AuthGate
import com.agarthavision.domain.usecase.auth.ObserveSignedOutNoticeUseCase
import com.agarthavision.domain.usecase.auth.ResolveAuthGateUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-scoped ViewModel exposing the persisted [ThemeMode] that drives
 * AgarthaVisionTheme's dark flag, the first-run [AuthGate], and whether the server has signed
 * this phone out.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    resolveAuthGateUseCase: ResolveAuthGateUseCase,
    observeSignedOutNoticeUseCase: ObserveSignedOutNoticeUseCase,
    onboardingPreferenceRepository: OnboardingPreferenceRepository,
    biometricLockRepository: BiometricLockRepository,
) : ViewModel() {

    val isBiometricLockEnabled: StateFlow<Boolean> = biometricLockRepository.isBiometricLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun lockApp() {
        _isLocked.value = true
    }

    fun unlockApp() {
        _isLocked.value = false
    }

    private val _authGate = MutableStateFlow<AuthGate>(AuthGate.Loading)

    /**
     * Whether first-run sign-in is still owed. Starts [AuthGate.Loading]; the splash is
     * held on that value so the Dashboard never flashes behind the login screen.
     */
    val authGate: StateFlow<AuthGate> = _authGate.asStateFlow()

    /**
     * Whether the first-run onboarding intro has been seen. Null while loading from DataStore.
     */
    val hasSeenOnboarding: StateFlow<Boolean?> = onboardingPreferenceRepository.hasSeenOnboarding
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _authGate.value = resolveAuthGateUseCase() }
    }

    /**
     * True once the server has signed this phone out (14zcqntjph8), until the next sign-in.
     * The wipe usually runs in a background sync while some screen is open; this is what takes
     * the medtech from that screen to the login screen. Eager, so a wipe while no one is
     * collecting is still seen on the next composition.
     */
    val signedOutByServer: StateFlow<Boolean> = observeSignedOutNoticeUseCase()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Active theme mode; light until the persisted preference loads. */
    val themeMode: StateFlow<ThemeMode> = observeThemeModeUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
}
