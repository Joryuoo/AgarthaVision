package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.LocalIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the DataStore-backed identity cache read path of [SupabaseAuthRepository]
 * (per ADR-007). The Supabase-dependent write path ([SupabaseAuthRepository.signIn])
 * requires an authenticated [io.github.jan.supabase.SupabaseClient] and is covered by
 * the instrumented / manual QA pass instead (see stage 03).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalIdentityCacheTest {

    private val fakeDataStore = FakeIdentityDataStore()

    @Test
    fun `observeLocalIdentity emits null when no identity has ever been cached`() = runTest {
        val identity = fakeDataStore.data
            .map { it[USER_ID_KEY]?.let { id -> LocalIdentity(id, it[EMAIL_KEY].orEmpty()) } }
            .first()

        assertNull(identity)
    }

    @Test
    fun `observeLocalIdentity reflects a cached user id and email`() = runTest {
        fakeDataStore.state.value = mutablePreferencesOf(
            USER_ID_KEY to "user-123",
            EMAIL_KEY to "medtech@citu.edu",
        )

        val identity = fakeDataStore.data
            .map { prefs ->
                prefs[USER_ID_KEY]?.let { id ->
                    LocalIdentity(userId = id, email = prefs[EMAIL_KEY].orEmpty(), displayName = prefs[DISPLAY_NAME_KEY])
                }
            }
            .first()

        assertEquals(LocalIdentity(userId = "user-123", email = "medtech@citu.edu", displayName = null), identity)
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("local_identity_user_id")
        val EMAIL_KEY = stringPreferencesKey("local_identity_email")
        val DISPLAY_NAME_KEY = stringPreferencesKey("local_identity_display_name")
    }
}

/** Minimal in-memory [DataStore] fake — mirrors this repo's fake-DAO test convention. */
private class FakeIdentityDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.map { it }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
