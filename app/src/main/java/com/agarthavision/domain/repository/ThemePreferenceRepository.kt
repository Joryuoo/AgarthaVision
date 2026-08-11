package com.agarthavision.domain.repository

import com.agarthavision.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Device-local persistence for the user's [ThemeMode] selection.
 */
interface ThemePreferenceRepository {

    /** Emits the persisted mode, starting with [ThemeMode.LIGHT] when unset. */
    val themeMode: Flow<ThemeMode>

    /** Persists [mode] as the active theme. */
    suspend fun setThemeMode(mode: ThemeMode)
}
