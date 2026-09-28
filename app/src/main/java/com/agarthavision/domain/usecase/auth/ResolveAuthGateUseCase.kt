package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Decides whether the app may open straight to the Dashboard or must force sign-in first.
 *
 * **Gates on the cached identity, not on live authentication.** `currentLocalUserId()`
 * survives token expiry and offline cold starts; `isAuthenticated()` does not. Gating on
 * the latter would send a medtech standing in a barangay with no signal back to a login
 * screen whose submit button is correctly disabled offline
 * ([com.agarthavision.ui.login.LoginViewModel] blocks submit with no connectivity), which
 * is the exact opposite of what this app is for.
 *
 * So the gate is **first-run only**: once any medtech has signed in on this device, it is
 * satisfied forever after. It exists because a Patient must belong to a User and a Session
 * to a Patient — with no user there is nothing to attach a patient to.
 */
class ResolveAuthGateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AuthGate =
        if (authRepository.currentLocalUserId() != null) AuthGate.Authed else AuthGate.NeedsLogin
}

/** Whether the first-run sign-in gate has been satisfied. */
sealed interface AuthGate {
    /** Still reading the cached identity. The splash is held here. */
    data object Loading : AuthGate

    /** A medtech has signed in on this device at some point. Open normally. */
    data object Authed : AuthGate

    /** Nobody has ever signed in here. Login is the start destination and cannot be left. */
    data object NeedsLogin : AuthGate
}
