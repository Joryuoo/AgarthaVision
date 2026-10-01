package com.agarthavision.data.repository

import com.agarthavision.domain.model.AccountAccess
import com.agarthavision.domain.repository.AccountAccessRepository
import com.agarthavision.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Supabase-backed [AccountAccessRepository] (14zcqntjph8).
 *
 * **The signal is a refused session renewal, and nothing narrower.** The Admin Console removes
 * a medtech by deleting the auth user or banning it (14zcqntjnt1, 14zcqntjnt5). Either way the
 * server stops renewing the phone's login: a deleted user's refresh tokens go with it, and a
 * banned one is answered `user_banned`. Matching on the refusal rather than on one error code
 * means the app does not care which of the two the console chose, nor which provider later
 * replaces Supabase (D7).
 *
 * **Why the app renews the login itself.** supabase-kt (3.0.3) renews in the background, and on
 * a 4xx it drops the session without surfacing the reason. So two states mean "refused":
 * - the SDK holds no session while an identity is still cached. Our own [AuthRepository.signOut]
 *   clears the cached identity before the SDK's session, so it never produces this; only the
 *   SDK dropping the session after a refusal, or the stored session vanishing, does. Either way
 *   this phone can never sync for that account again without a fresh sign-in;
 * - a renewal this class asks for is answered with a client error.
 *
 * **What is never a refusal:** no connection, a timeout, any 5xx, 408 and 429 (the server is
 * busy, not refusing), and a token that expired offline, which the SDK reports as
 * [SessionStatus.RefreshFailure] and renews once the signal returns.
 *
 * A successful answer is trusted for [CONFIRMATION_TTL], so a sync that runs after every save
 * does not renew the login every few seconds.
 */
@Singleton
class SupabaseAccountAccessRepository @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
    private val authRepository: AuthRepository,
) : AccountAccessRepository {

    private val mutex = Mutex()
    private var lastAllowed: TimeSource.Monotonic.ValueTimeMark? = null

    override suspend fun checkAccountAccess(): AccountAccess = mutex.withLock {
        if (authRepository.currentLocalUserId() == null) return@withLock AccountAccess.UNKNOWN
        val auth = supabaseProvider.get().auth
        auth.awaitInitialization()
        when (auth.sessionStatus.value) {
            is SessionStatus.NotAuthenticated -> AccountAccess.REFUSED
            is SessionStatus.Authenticated -> if (recentlyAllowed()) AccountAccess.ALLOWED else renew()
            // Initializing cannot outlast awaitInitialization. RefreshFailure is the SDK saying
            // "no network" or "server error", and it keeps retrying on its own.
            else -> AccountAccess.UNKNOWN
        }
    }

    private fun recentlyAllowed(): Boolean =
        lastAllowed?.let { it.elapsedNow() < CONFIRMATION_TTL } ?: false

    private suspend fun renew(): AccountAccess {
        val access = runCatching { supabaseProvider.get().auth.refreshCurrentSession() }.fold(
            onSuccess = { AccountAccess.ALLOWED },
            onFailure = { error ->
                if (error is CancellationException) throw error
                // Anything but an HTTP answer — network, timeout, serialization — is nothing the
                // server said.
                (error as? RestException)?.let { accessForRenewalStatus(it.statusCode) } ?: AccountAccess.UNKNOWN
            },
        )
        lastAllowed = if (access == AccountAccess.ALLOWED) TimeSource.Monotonic.markNow() else null
        return access
    }

    private companion object {
        val CONFIRMATION_TTL = 5.minutes
    }
}

/**
 * Maps the HTTP status of a refused renewal to a verdict. Top-level and internal so the one
 * rule that decides whether a phone is wiped is tested without a Supabase client.
 */
internal fun accessForRenewalStatus(statusCode: Int): AccountAccess = when (statusCode) {
    HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS -> AccountAccess.UNKNOWN
    in HTTP_CLIENT_ERRORS -> AccountAccess.REFUSED
    else -> AccountAccess.UNKNOWN
}

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_CLIENT_ERRORS = 400..499
