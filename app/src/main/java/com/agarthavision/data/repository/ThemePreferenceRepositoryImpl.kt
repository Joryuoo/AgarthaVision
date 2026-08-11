package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.ThemePreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [ThemePreferenceRepository]. Unknown or missing stored
 * values fall back to [ThemeMode.LIGHT].
 */
class ThemePreferenceRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemePreferenceRepository {

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY]
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.LIGHT
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
