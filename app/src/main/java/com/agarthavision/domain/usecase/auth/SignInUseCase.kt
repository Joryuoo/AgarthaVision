package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs in a dashboard-provisioned medtech account.
 */
class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    /**
     * Attempts email/password sign-in and returns the failure for UI handling.
     */
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        runCatching { authRepository.signIn(email, password) }
}
