package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs the current medtech out per ADR-008.
 *
 * This used to be blocked while a capture session was active, on the reasoning that the medtech
 * should end the smear first. Sessions do not end any more (86d4ab4vm), so that guard became a
 * permanent block on signing out. Sign-out now **detaches** from the session instead:
 * [SessionManager.clearActive] leaves it open and unfinished, exactly as it was.
 *
 * Nothing is stranded by that. A sample keeps the owner it was captured under — cached identity
 * or null — and is claimed at the next login (ADR-007), and capture stops when the screen does.
 *
 * The old doc here also claimed flagged frames were cleared on the way out by
 * `FlaggedFrameStore.clear`. That was already untrue: `clear()` has no callers.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val discardUnsyncedDataUseCase: DiscardUnsyncedDataUseCase,
) {
    /** Signs out, detaching from any active session and discarding unsynced work first. */
    suspend operator fun invoke(): Result<Unit> = runCatching {
        sessionManager.clearActive()
        // Before signOut, not after: the discard is scoped by the cached identity, and
        // signOut is what clears it. Reversed, this would silently discard nothing.
        authRepository.currentLocalUserId()?.let { userId -> discardUnsyncedDataUseCase(userId) }
        authRepository.signOut()
    }
}
