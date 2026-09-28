package com.agarthavision.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers whether the last pull pass finished cleanly, per user.
 *
 * Sync runs in a worker now, so the pass that fails usually has nobody on screen watching it.
 * Without this the Settings card can only describe the push queue, and a pull that threw on
 * every entity type leaves no trace a medtech can see - which is exactly how a parse failure on
 * all four types went unnoticed while the card read "All synced".
 *
 * Per user id, and stored beside [InitialFetchStateStore] in the app-scoped DataStore, because
 * "did my data arrive" is a question about an account rather than a device.
 */
interface FetchOutcomeStore {
    /** Emits `true` while the last recorded pull pass for [userId] left something unfetched. */
    fun observeIncomplete(userId: String): Flow<Boolean>

    /** Records how a pull pass ended. [complete] clears the flag; anything else sets it. */
    suspend fun record(userId: String, complete: Boolean)
}

class DataStoreFetchOutcomeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : FetchOutcomeStore {

    override fun observeIncomplete(userId: String): Flow<Boolean> =
        dataStore.data.map { prefs -> userId in (prefs[KEY] ?: emptySet()) }

    override suspend fun record(userId: String, complete: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            prefs[KEY] = if (complete) current - userId else current + userId
        }
    }

    private companion object {
        private val KEY = stringSetPreferencesKey("incomplete_fetch_user_ids")
    }
}
