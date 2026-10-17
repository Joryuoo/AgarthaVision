package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.agarthavision.domain.repository.OnboardingPreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OnboardingPreferenceRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferenceRepository {

    override val hasSeenOnboarding: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_HAS_SEEN_ONBOARDING] ?: false
    }

    override suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    private companion object {
        val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }
}
