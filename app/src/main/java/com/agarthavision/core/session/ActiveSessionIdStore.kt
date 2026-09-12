package com.agarthavision.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Remembers which session the medtech is working in, across process death.
 *
 * Needed because sessions no longer end (86d4ab4vm). [SessionManager.state] is in-memory, and
 * while sessions were closed explicitly that was invisible — the medtech ended the smear and
 * the state going idle was correct. Now a session stays open indefinitely, so an app restart
 * would drop to idle with a session that is still live: the dashboard card would vanish and the
 * verification queue would render empty, because `FlaggedFrameStore` emits an empty list
 * whenever there is no active session. Nothing would be lost, but it would look exactly like
 * everything was.
 *
 * **The session id is stored, never "the newest open session".** With sessions that never end,
 * "newest open" is a guess, and a wrong guess attributes captures to the wrong smear — which in
 * this app is the wrong patient.
 */
interface ActiveSessionIdStore {
    suspend fun read(): String?
    suspend fun write(sessionId: String?)
}

class DataStoreActiveSessionIdStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ActiveSessionIdStore {

    override suspend fun read(): String? =
        dataStore.data.first()[KEY]?.takeIf { it.isNotBlank() }

    override suspend fun write(sessionId: String?) {
        dataStore.edit { preferences ->
            if (sessionId == null) {
                preferences.remove(KEY)
            } else {
                preferences[KEY] = sessionId
            }
        }
    }

    private companion object {
        private val KEY = stringPreferencesKey("active_session_id")
    }
}
