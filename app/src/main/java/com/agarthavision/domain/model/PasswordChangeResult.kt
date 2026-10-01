package com.agarthavision.domain.model

/**
 * What became of a medtech's request to change their own password from Settings (14zcqntjph9).
 *
 * Provider-neutral on purpose (D7): each case is something the medtech can act on, not an auth
 * provider's error code. The Supabase mapping lives in
 * [com.agarthavision.data.repository.SupabaseAuthRepository].
 */
sealed interface PasswordChangeResult {
    /** The new password is set. This phone stays signed in. */
    data object Changed : PasswordChangeResult

    /** The current password did not match. Nothing changed. */
    data object WrongCurrentPassword : PasswordChangeResult

    /**
     * The provider's password rules refused the new password.
     *
     * @property detail the provider's own explanation (for example the minimum length it
     *   enforces), shown under the field when it gave one. The rules live with the provider so
     *   the app and the Admin Console's sign-in can never disagree about them.
     */
    data class WeakPassword(val detail: String?) : PasswordChangeResult

    /** The new password is the one already in use. */
    data object SamePassword : PasswordChangeResult

    /** No connection reached the server. Nothing changed. */
    data object NoConnection : PasswordChangeResult

    /**
     * The current password was accepted and the new one sent, but the connection dropped before
     * the answer came back. The server may have set it, so the medtech is not told nothing
     * changed.
     */
    data object Unconfirmed : PasswordChangeResult

    /** Anything else the server answered. Nothing the medtech typed is at fault. */
    data object Failed : PasswordChangeResult
}
