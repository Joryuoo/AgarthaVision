package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.SignedOutNoticeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [SignedOutNoticeStore] in the app-scoped settings DataStore. One key: present means pending. */
class DataStoreSignedOutNoticeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SignedOutNoticeStore {

    override fun observe(): Flow<SignedOutNotice?> =
        dataStore.data.map { prefs -> prefs[UNSYNCED_KEPT_KEY]?.let(::SignedOutNotice) }

    override suspend fun record(notice: SignedOutNotice) {
        dataStore.edit { prefs -> prefs[UNSYNCED_KEPT_KEY] = notice.unsyncedKept }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(UNSYNCED_KEPT_KEY) }
    }

    private companion object {
        val UNSYNCED_KEPT_KEY = intPreferencesKey("signed_out_by_server_unsynced_kept")
    }
}
