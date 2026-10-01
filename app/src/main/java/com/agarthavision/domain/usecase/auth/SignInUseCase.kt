package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SignedOutNoticeStore
import javax.inject.Inject

/**
 * Signs in a dashboard-provisioned medtech account.
 */
class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val signedOutNoticeStore: SignedOutNoticeStore,
) {
    /**
     * Attempts email/password sign-in and returns the failure for UI handling. A success
     * retires any "signed out by the server" notice (14zcqntjph8): whoever signed in has read it.
     */
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        runCatching {
            authRepository.signIn(email, password)
            signedOutNoticeStore.clear()
        }
}
