package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.AccountAccess
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.AccountAccessRepository
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SignedOutNoticeStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs the phone out and removes its synced data when the server no longer accepts the
 * account's login (14zcqntjph8). Unsynced work stays; see [WipeLocalAccountDataUseCase].
 *
 * Runs at the start of every sync pass ([com.agarthavision.data.sync.SyncWorker]), which is
 * every time the phone has signal after app start, a save, or reconnecting. So the first
 * moment a deactivated medtech's phone reaches the server is the moment it is wiped. A password
 * changed elsewhere ends here too, and costs the medtech only a sign-in.
 *
 * **Offline, nothing happens.** The check is skipped without a connection, and anything short of
 * a refusal the server gave is [AccountAccess.UNKNOWN], which changes nothing. That is what
 * keeps a medtech in a barangay with no signal, or with a token that expired overnight, working.
 *
 * Order on a refusal:
 * 1. detach from the active session, so nothing captures into data about to be removed;
 * 2. wipe ([WipeLocalAccountDataUseCase]), which counts the identity's kept work, so before step 4;
 * 3. record the notice, so the login screen can say why even if the process dies next;
 * 4. sign out, which clears the identity and sends the app back to the login screen.
 *
 * A singleton with a lock, because the worker and a screen may both ask at once and a second
 * wipe racing the first would find the identity half-cleared.
 */
@Singleton
class EnforceAccountAccessUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountAccessRepository: AccountAccessRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager: SessionManager,
    private val wipeLocalAccountDataUseCase: WipeLocalAccountDataUseCase,
    private val signedOutNoticeStore: SignedOutNoticeStore,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(): AccountAccess = mutex.withLock {
        val userId = authRepository.currentLocalUserId()
        if (userId == null || !connectivityObserver.currentlyOnline()) return@withLock AccountAccess.UNKNOWN

        val access = accountAccessRepository.checkAccountAccess()
        if (access == AccountAccess.REFUSED) {
            sessionManager.clearActive()
            val unsyncedKept = wipeLocalAccountDataUseCase(userId)
            signedOutNoticeStore.record(SignedOutNotice(unsyncedKept = unsyncedKept))
            authRepository.signOut()
        }
        access
    }
}
