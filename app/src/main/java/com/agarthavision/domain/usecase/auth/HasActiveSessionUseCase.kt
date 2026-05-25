package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Checks whether the user can skip Login on cold start.
 */
class HasActiveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    /**
     * Returns true when a persisted Supabase session is available.
     */
    suspend operator fun invoke(): Boolean = authRepository.hasActiveSession()
}
