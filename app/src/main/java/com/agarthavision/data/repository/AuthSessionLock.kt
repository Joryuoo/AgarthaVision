package com.agarthavision.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialises the two things that swap or test this phone's Supabase session on purpose: a
 * password change ([SupabaseAuthRepository.changePassword]) and the account check
 * ([SupabaseAccountAccessRepository.checkAccountAccess]).
 *
 * A password change ends every other session of the account, and the session this phone held
 * before the change is one of them. A renewal of that old session still in flight when the
 * password changes is answered 4xx, which the account check reads as "the server refused this
 * account" and wipes the phone that made the change (14zcqntjph9, 14zcqntjph8). Holding one lock
 * across both means the check renews either the old session before the change starts or the
 * new one after it ends.
 */
@Singleton
class AuthSessionLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
