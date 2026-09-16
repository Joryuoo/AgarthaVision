package com.agarthavision.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Tracks whether the initial pull-from-server fetch has completed for each user.
 *
 * Before the first successful [FetchRemoteDataUseCase] pass the remote dataset has never been
 * pulled to the device, so showing "All synced" would be a false positive. This store records
 * completion per user id so the Settings badge and Dashboard can show NOT_YET_SYNCED until the
 * first pull succeeds.
 *
 * Persisted in the app-scoped DataStore so it survives process death and is per-account.
 */
interface InitialFetchStateStore {
    /** Emits `true` once [markCompleted] has been called for [userId]; `false` before that. */
    fun observeCompleted(userId: String): Flow<Boolean>

    /** Records that the first full fetch pass for [userId] succeeded. */
    suspend fun markCompleted(userId: String)

    /** Clears the completed flag for [userId] (e.g. on sign-out or account switch). */
    suspend fun clear(userId: String)
}

class DataStoreInitialFetchStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : InitialFetchStateStore {

    override fun observeCompleted(userId: String): Flow<Boolean> =
        dataStore.data.map { prefs ->
            userId in (prefs[KEY] ?: emptySet())
        }

    override suspend fun markCompleted(userId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            prefs[KEY] = current + userId
        }
    }

    override suspend fun clear(userId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            prefs[KEY] = current - userId
        }
    }

    private companion object {
        private val KEY = stringSetPreferencesKey("initial_fetch_completed_user_ids")
    }
}
