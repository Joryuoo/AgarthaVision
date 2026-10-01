package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import javax.inject.Inject

/**
 * Supabase-backed implementation of [AuthRepository].
 *
 * On a successful sign-in the resulting identity is cached to the settings DataStore so
 * that offline sessions can be attributed to the last medtech without a live token.
 * Per ADR-007.
 */
class SupabaseAuthRepository @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
    private val dataStore: DataStore<Preferences>,
) : AuthRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()

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

    /**
     * Supabase does not ask for the current password before an update; checking it is our choice
     * (14zcqntjph9). It is checked by signing in with it again, which works on every project
     * setting and replaces this phone's session with a fresh one for the same account. That fresh
     * session also satisfies Supabase's "secure password change", which wants a recent sign-in.
     *
     * Supabase then ends every other session of the account, so other phones and the Admin
     * Console need the new password: their next login renewal is refused.
     */
    override suspend fun changePassword(currentPassword: String, newPassword: String): PasswordChangeResult {
        val email = signedInEmail() ?: return PasswordChangeResult.Failed
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = currentPassword
            }
        }.fold(
            onSuccess = {
                runCatching { supabase.auth.updateUser { password = newPassword } }.fold(
                    onSuccess = { PasswordChangeResult.Changed },
                    onFailure = ::passwordUpdateFailure,
                )
            },
            onFailure = ::passwordCheckFailure,
        )
    }

    /** The live session's email, else the one this phone signed in with. */
    private suspend fun signedInEmail(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentUserOrNull()?.email?.takeIf { it.isNotBlank() }
            ?: dataStore.data.first()[EMAIL_KEY]?.takeIf { it.isNotBlank() }
    }

    override suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
        dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(DISPLAY_NAME_KEY)
        }
    }

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

/**
 * Maps a failed check of the current password to what the medtech can act on (14zcqntjph9).
 * Top-level and internal so the mapping is tested without a Supabase client.
 */
internal fun passwordCheckFailure(error: Throwable): PasswordChangeResult = when {
    error is CancellationException -> throw error
    error.isNoConnection() -> PasswordChangeResult.NoConnection
    error is RestException && error.error == INVALID_CREDENTIALS -> PasswordChangeResult.WrongCurrentPassword
    else -> PasswordChangeResult.Failed
}

/** Maps a failed update of the password, after the current one was accepted (14zcqntjph9). */
internal fun passwordUpdateFailure(error: Throwable): PasswordChangeResult = when {
    error is CancellationException -> throw error
    error.isNoConnection() -> PasswordChangeResult.NoConnection
    error is AuthWeakPasswordException -> PasswordChangeResult.WeakPassword(error.message?.takeIf { it.isNotBlank() })
    error is RestException && error.error == SAME_PASSWORD -> PasswordChangeResult.SamePassword
    else -> PasswordChangeResult.Failed
}

// supabase-kt wraps every transport failure in HttpRequestException except a timeout, which is
// Ktor's HttpRequestTimeoutException, an IOException.
private fun Throwable.isNoConnection(): Boolean = this is HttpRequestException || this is IOException

private const val INVALID_CREDENTIALS = "invalid_credentials"
private const val SAME_PASSWORD = "same_password"
