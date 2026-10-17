package com.agarthavision.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.sync.LastSyncStore
import com.agarthavision.domain.sync.SyncCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreLastSyncStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LastSyncStore {

    override fun observe(userId: String): Flow<SyncCompletion?> {
        val timeKey = longPreferencesKey("last_sync_time_$userId")
        val countKey = intPreferencesKey("last_sync_count_$userId")
        return dataStore.data.map { prefs ->
            val time = prefs[timeKey]
            val count = prefs[countKey]
            if (time != null && count != null) {
                SyncCompletion(completedAtMillis = time, itemsSynced = count)
            } else {
                null
            }
        }
    }

    override suspend fun record(userId: String, completion: SyncCompletion) {
        val timeKey = longPreferencesKey("last_sync_time_$userId")
        val countKey = intPreferencesKey("last_sync_count_$userId")
        dataStore.edit { prefs ->
            prefs[timeKey] = completion.completedAtMillis
            prefs[countKey] = completion.itemsSynced
        }
    }

    override fun observeLastError(userId: String): Flow<String?> {
        val errorKey = stringPreferencesKey("last_sync_error_$userId")
        return dataStore.data.map { prefs -> prefs[errorKey] }
    }

    override suspend fun recordLastError(userId: String, error: String) {
        val errorKey = stringPreferencesKey("last_sync_error_$userId")
        dataStore.edit { prefs ->
            prefs[errorKey] = error
        }
    }
}
