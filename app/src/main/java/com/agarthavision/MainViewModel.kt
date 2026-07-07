package com.agarthavision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Activity-scoped ViewModel exposing the persisted [ThemeMode] that drives
 * AgarthaVisionTheme's dark flag.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
) : ViewModel() {

    /** Active theme mode; light until the persisted preference loads. */
    val themeMode: StateFlow<ThemeMode> = observeThemeModeUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)
}
