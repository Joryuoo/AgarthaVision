package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.LaboratoryBranding
import com.agarthavision.domain.model.LocalIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the same DataStore operations that [SupabaseAuthRepository] uses for offline identity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalIdentityCacheTest {

    private val fakeDataStore = FakeIdentityDataStore()
    private val cache = LocalIdentityCache(fakeDataStore)
    private val laboratory = LaboratoryBranding("Cebu City Laboratory", byteArrayOf(1, 2))

    @Test
    fun `observeLocalIdentity emits null when no identity has ever been cached`() = runTest {
        assertNull(cache.observeIdentity().first())
    }

    @Test
    fun `legacy identity keys open without branding and default to pending profile`() = runTest {
        fakeDataStore.state.value = mutablePreferencesOf(
            USER_ID_KEY to "user-123",
            EMAIL_KEY to "medtech@citu.edu",
        )

        val identity = cache.observeIdentity().first()

        assertEquals(
            LocalIdentity(
                userId = "user-123",
                email = "medtech@citu.edu",
                displayName = LocalIdentity.PENDING_PROFILE_PLACEHOLDER,
            ),
            identity,
        )
    }

    @Test
    fun `observeLocalIdentity reflects a cached display name when present`() = runTest {
        fakeDataStore.state.value = mutablePreferencesOf(
            USER_ID_KEY to "user-123",
            EMAIL_KEY to "medtech@citu.edu",
            DISPLAY_NAME_KEY to "Maria Santos",
        )

        val identity = cache.observeIdentity().first()

        assertEquals(
            LocalIdentity(
                userId = "user-123",
                email = "medtech@citu.edu",
                displayName = "Maria Santos",
            ),
            identity,
        )
    }

    @Test
    fun `cached name and logo are available from a new reader offline`() = runTest {
        cache.saveIdentity(USER, "medtech@citu.edu", "Medtech")
        cache.saveLaboratory(USER, laboratory) { true }

        val identity = LocalIdentityCache(fakeDataStore).observeIdentity().first()

        assertEquals(laboratory, identity?.laboratory)
    }

    @Test
    fun `a successful read without a logo removes the old logo and keeps the name`() = runTest {
        cache.saveIdentity(USER, "medtech@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { true }
        cache.saveLaboratory(USER, LaboratoryBranding(laboratory.name)) { true }

        assertEquals(LaboratoryBranding(laboratory.name), cache.observeIdentity().first()?.laboratory)
    }

    @Test
    fun `a successful read without a laboratory removes its name and logo`() = runTest {
        cache.saveIdentity(USER, "medtech@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { true }
        cache.saveLaboratory(USER, null) { true }

        assertNull(cache.observeIdentity().first()?.laboratory)
    }

    @Test
    fun `a delayed read cannot restore branding after switching accounts`() = runTest {
        cache.saveIdentity(USER, "medtech@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { true }
        cache.saveIdentity("user-2", "other@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { true }

        assertEquals("user-2", cache.currentUserId())
        assertNull(cache.observeIdentity().first()?.laboratory)
    }

    @Test
    fun `sign-out clears the identity and a result from the old session is ignored`() = runTest {
        cache.saveIdentity(USER, "medtech@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { true }
        cache.clear()
        cache.saveLaboratory(USER, laboratory) { true }
        assertNull(cache.observeIdentity().first())

        cache.saveIdentity(USER, "medtech@citu.edu", null)
        cache.saveLaboratory(USER, laboratory) { false }
        assertNull(cache.observeIdentity().first()?.laboratory)
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("local_identity_user_id")
        val EMAIL_KEY = stringPreferencesKey("local_identity_email")
        val DISPLAY_NAME_KEY = stringPreferencesKey("local_identity_display_name")
        const val USER = "user-1"
    }
}

/** Minimal in-memory [DataStore] fake — mirrors this repo's fake-DAO test convention. */
private class FakeIdentityDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
