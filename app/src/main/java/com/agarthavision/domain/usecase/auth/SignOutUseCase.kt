package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs the current medtech out per ADR-008.
 *
 * Blocked while a capture session is [SessionState.Active] — the medtech must end the
 * session first, since offline sign-out during an active smear would strand its remaining
 * captures under an identity that's about to be cleared. Any not-yet-submitted flagged
 * frames for the (now-ended) session are already cleared by
 * [com.agarthavision.data.repository.FlaggedFrameStore.clear], consistent with the
 * documented "flagged frames are lost on logout" behavior (CONTEXT.md, sample lifecycle).
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    /**
     * Signs out, or fails with [IllegalStateException] when a capture session is active.
     */
    suspend operator fun invoke(): Result<Unit> = runCatching {
        check(sessionManager.state.value !is SessionState.Active) {
            "End the active session before signing out."
        }
        authRepository.signOut()
    }
}
