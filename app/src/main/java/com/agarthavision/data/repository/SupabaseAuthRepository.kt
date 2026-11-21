package com.agarthavision.data.repository

import com.agarthavision.core.util.Logger
import com.agarthavision.data.supabase.LaboratoryBrandingRemoteDataSource
import com.agarthavision.domain.model.HumanCheckRejectedException
import com.agarthavision.domain.model.LaboratoryAccessInactiveException
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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val identityCache: LocalIdentityCache,
    private val sessionLock: AuthSessionLock,
    private val brandingRemote: LaboratoryBrandingRemoteDataSource,
) : AuthRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()

    override fun observeLocalIdentity(): Flow<LocalIdentity?> = identityCache.observeIdentity()

    override suspend fun currentLocalUserId(): String? = identityCache.currentUserId()

    override suspend fun isAuthenticated(): Boolean {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun hasActiveSession(): Boolean {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun signIn(email: String, password: String, humanCheckToken: String?) {
        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
                captchaToken = humanCheckToken
            }
        } catch (error: RestException) {
            if (error.isHumanCheckRefusal()) throw HumanCheckRejectedException(error)
            if (error.isBannedRefusal()) throw LaboratoryAccessInactiveException(error)
            throw error
        }
        val user = supabase.auth.currentUserOrNull() ?: return
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
        identityCache.saveIdentity(user.id, email, displayName)
        // Give the medtech a chance to leave the clinic with branding already on the device.
        // The normal sync pass can retry without holding up sign-in on a slow connection.
        withTimeoutOrNull(SIGN_IN_BRANDING_TIMEOUT_MILLIS) { refreshLaboratoryBranding() }
    }

    override suspend fun getCurrentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    /**
     * Supabase does not ask for the current password before an update; checking it is our choice
     * (14zcqntjph9). It is checked by signing in with it again, which works on every project
     * setting and replaces this phone's session with a fresh one for the same account. That fresh
     * session also satisfies Supabase's "secure password change", which wants a recent sign-in.
     *
     * Supabase then ends every other session of the account, so other phones and the Admin
     * Console need the new password: their next login renewal is refused. This phone's old
     * session is one of those, so the account check waits on [AuthSessionLock] meanwhile.
     */
    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        humanCheckToken: String?,
    ): PasswordChangeResult =
        sessionLock.withLock {
            val email = signedInEmail() ?: return@withLock PasswordChangeResult.Failed
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = currentPassword
                    captchaToken = humanCheckToken
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
            ?: identityCache.observeIdentity().first()?.email?.takeIf { it.isNotBlank() }
    }

    override suspend fun signOut() {
        // Identity first, then the Supabase session. A cached identity with no Supabase session
        // is what SupabaseAccountAccessRepository reads as "the server refused this account"
        // (14zcqntjph8), so the medtech's own sign-out must never pass through that state.
        identityCache.clear()
        runCatching { supabase.auth.signOut() }
    }

    /** Updates the offline snapshot after sign-in and during online sync. */
    internal suspend fun refreshLaboratoryBranding() {
        runCatching {
            sessionLock.withLock {
                supabase.auth.awaitInitialization()
                val session = supabase.auth.currentSessionOrNull() ?: return@withLock
                val userId = session.user?.id ?: return@withLock
                if (identityCache.currentUserId() != userId) return@withLock

                val branding = brandingRemote.fetch()
                identityCache.saveLaboratory(userId, branding) {
                    supabase.auth.currentSessionOrNull()?.accessToken == session.accessToken
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Logger.w(TAG, "Laboratory branding refresh failed (${error.javaClass.simpleName})")
        }
    }

    private companion object {
        const val TAG = "SupabaseAuthRepository"
        const val SIGN_IN_BRANDING_TIMEOUT_MILLIS = 2_500L
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
    error is RestException && error.isHumanCheckRefusal() -> PasswordChangeResult.HumanCheckRejected
    else -> PasswordChangeResult.Failed
}

/**
 * Maps a failed update of the password, after the current one was accepted (14zcqntjph9). A
 * connection lost here may have lost the answer rather than the request, so it is
 * [PasswordChangeResult.Unconfirmed], never "nothing changed".
 */
internal fun passwordUpdateFailure(error: Throwable): PasswordChangeResult = when {
    error is CancellationException -> throw error
    error.isNoConnection() -> PasswordChangeResult.Unconfirmed
    error is AuthWeakPasswordException -> PasswordChangeResult.WeakPassword(error.message?.takeIf { it.isNotBlank() })
    error is RestException && error.error == SAME_PASSWORD -> PasswordChangeResult.SamePassword
    else -> PasswordChangeResult.Failed
}

// supabase-kt wraps every transport failure in HttpRequestException except a timeout, which is
// Ktor's HttpRequestTimeoutException, an IOException.
private fun Throwable.isNoConnection(): Boolean = this is HttpRequestException || this is IOException

/**
 * Whether the provider refused the sign-in's human check (14zcqntnqxf): missing, expired, already
 * used or failed. Supabase answers every one of those with `captcha_failed`.
 */
internal fun RestException.isHumanCheckRefusal(): Boolean = error == CAPTCHA_FAILED

/**
 * Whether the provider refused the sign-in because the account is banned (14zcqnttvpa): the Admin
 * Console bans a deactivated laboratory's accounts, and Supabase answers `user_banned`.
 */
internal fun RestException.isBannedRefusal(): Boolean = error == USER_BANNED

private const val INVALID_CREDENTIALS = "invalid_credentials"
private const val CAPTCHA_FAILED = "captcha_failed"
private const val USER_BANNED = "user_banned"
private const val SAME_PASSWORD = "same_password"
