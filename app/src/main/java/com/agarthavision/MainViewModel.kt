package com.agarthavision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.AuthGate
import com.agarthavision.domain.usecase.auth.ResolveAuthGateUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-scoped ViewModel exposing the persisted [ThemeMode] that drives
 * AgarthaVisionTheme's dark flag, the first-run [AuthGate], and re-attaching to the open
 * session at launch.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    resolveAuthGateUseCase: ResolveAuthGateUseCase,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _authGate = MutableStateFlow<AuthGate>(AuthGate.Loading)

    /**
     * Whether first-run sign-in is still owed. Starts [AuthGate.Loading]; the splash is
     * held on that value so the Dashboard never flashes behind the login screen.
     */
    val authGate: StateFlow<AuthGate> = _authGate.asStateFlow()

    init {
        viewModelScope.launch { _authGate.value = resolveAuthGateUseCase() }

        // Sessions no longer end, so one can outlive the process that created it. Without
        // this the app would come back idle with a smear still open: the dashboard card would
        // be gone and the verification queue would render empty, because FlaggedFrameStore
        // emits an empty list when there is no active session. Nothing lost, but it would look
        // like everything was.
        viewModelScope.launch { sessionManager.restoreActiveSession() }
    }

    /** Active theme mode; light until the persisted preference loads. */
    val themeMode: StateFlow<ThemeMode> = observeThemeModeUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
}
