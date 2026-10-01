package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Changes the signed-in medtech's password from Settings (14zcqntjph9).
 *
 * Online only: the password lives with the auth provider, so there is nothing to queue. Offline
 * it answers [PasswordChangeResult.NoConnection] without trying, which the screen turns into
 * "this needs a connection" rather than a silent failure.
 *
 * Touches no local data. Unsynced work stays queued under the same `user_id`, because the account
 * does not change, only how it signs in.
 */
class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
) {
    suspend operator fun invoke(currentPassword: String, newPassword: String): PasswordChangeResult =
        when {
            !connectivityObserver.currentlyOnline() -> PasswordChangeResult.NoConnection
            // Caught here as well as by the provider, so it costs no round trip and no sign-in.
            newPassword == currentPassword -> PasswordChangeResult.SamePassword
            else -> authRepository.changePassword(currentPassword, newPassword)
        }
}
