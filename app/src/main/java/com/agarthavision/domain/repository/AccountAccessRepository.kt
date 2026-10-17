package com.agarthavision.domain.repository

import com.agarthavision.domain.model.AccountAccess

/**
 * Asks the auth provider whether the cached account may still use the app (14zcqntjph8).
 *
 * Its own port rather than a method on [AuthRepository], because the answer is a verdict
 * about the account, not part of signing in or out, and nothing else needs it.
 */
interface AccountAccessRepository {
    /**
     * Asks the server, by renewing the login, whether the account is still accepted.
     *
     * Returns [AccountAccess.REFUSED] only for a refusal the server gave: a renewal answered
     * with a client error, or a session the provider has already dropped for one. Network
     * failures, 5xx, rate limiting and an expired-but-renewable token are
     * [AccountAccess.UNKNOWN] or [AccountAccess.ALLOWED], never a refusal.
     */
    suspend fun checkAccountAccess(): AccountAccess
}
