package com.agarthavision.domain.usecase.settings

import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.ThemePreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the persisted [ThemeMode]; emits [ThemeMode.LIGHT] until the user
 * chooses otherwise.
 */
class ObserveThemeModeUseCase @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
) {
    /** Stream of the active theme mode. */
    operator fun invoke(): Flow<ThemeMode> = themePreferenceRepository.themeMode
}
