package com.agarthavision.domain.usecase.settings

import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.ThemePreferenceRepository
import javax.inject.Inject

/**
 * Persists the user's [ThemeMode] selection.
 */
class SetThemeModeUseCase @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
) {
    /** Persists [mode] and returns the failure for UI handling. */
    suspend operator fun invoke(mode: ThemeMode): Result<Unit> =
        runCatching { themePreferenceRepository.setThemeMode(mode) }
}
