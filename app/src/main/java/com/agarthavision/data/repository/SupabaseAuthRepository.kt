package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * Supabase-backed implementation of [AuthRepository].
 *
 * On a successful sign-in the resulting identity is cached to the settings DataStore so
 * that offline sessions can be attributed to the last medtech without a live token.
 * Per ADR-007.
 */
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>,
) : AuthRepository {

    override fun observeLocalIdentity(): Flow<LocalIdentity?> =
        dataStore.data.map { preferences ->
            val userId = preferences[USER_ID_KEY] ?: return@map null
            LocalIdentity(
                userId = userId,
                email = preferences[EMAIL_KEY].orEmpty(),
                displayName = preferences[DISPLAY_NAME_KEY],
            )
        }

    override suspend fun currentLocalUserId(): String? =
        dataStore.data.first()[USER_ID_KEY]

    override suspend fun isAuthenticated(): Boolean {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun hasActiveSession(): Boolean {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        cacheIdentity(email)
    }

    override suspend fun getCurrentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    /** Persists the signed-in user's identity for offline attribution. */
    private suspend fun cacheIdentity(email: String) {
        val user = supabase.auth.currentUserOrNull() ?: return
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = user.id
            preferences[EMAIL_KEY] = email
            if (displayName.isNullOrBlank()) {
                preferences.remove(DISPLAY_NAME_KEY)
            } else {
                preferences[DISPLAY_NAME_KEY] = displayName
            }
        }
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("local_identity_user_id")
        val EMAIL_KEY = stringPreferencesKey("local_identity_email")
        val DISPLAY_NAME_KEY = stringPreferencesKey("local_identity_display_name")
    }
}
