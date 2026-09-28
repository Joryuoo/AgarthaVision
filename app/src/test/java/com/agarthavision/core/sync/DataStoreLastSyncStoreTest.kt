package com.agarthavision.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.agarthavision.domain.sync.SyncCompletion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreLastSyncStoreTest {

    private val fakeDataStore = FakePreferencesDataStore()
    private val store = DataStoreLastSyncStore(fakeDataStore)

    @Test
    fun `observe returns null before any record call`() = runTest {
        assertNull(store.observe("user-a").first())
    }

    @Test
    fun `record for user A does not affect observe for user B`() = runTest {
        store.record("user-a", SyncCompletion(completedAtMillis = 1_000L, itemsSynced = 3))

        assertEquals(
            SyncCompletion(completedAtMillis = 1_000L, itemsSynced = 3),
            store.observe("user-a").first(),
        )
        assertNull(store.observe("user-b").first())
    }
}

/** Minimal in-memory [DataStore] fake — mirrors this repo's fake-DAO test convention. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.map { it }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
