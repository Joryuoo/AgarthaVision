package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.agarthavision.domain.repository.BiometricLockRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BiometricLockRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BiometricLockRepository {

    override val isBiometricLockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_BIOMETRIC_LOCK] ?: false
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_LOCK] = enabled
        }
    }

    private companion object {
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("is_biometric_lock_enabled")
    }
}
