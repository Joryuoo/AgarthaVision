package com.agarthavision.data.repository

import com.agarthavision.domain.model.PasswordChangeResult
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Pins how Supabase's answers become something the medtech can act on (14zcqntjph9). The
 * wrong-current-password case is the one the ticket names; the rest keep a network blip from
 * reading as "wrong password".
 */
class PasswordChangeFailureTest {

    @Test
    fun `invalid credentials on the check mean the current password was wrong`() {
        val error = AuthRestException("invalid_credentials", "Invalid login credentials", 400)
        assertEquals(PasswordChangeResult.WrongCurrentPassword, passwordCheckFailure(error))
    }

    @Test
    fun `no connection on the check is never a wrong password`() {
        assertEquals(PasswordChangeResult.NoConnection, passwordCheckFailure(IOException("timeout")))
    }

    @Test
    fun `no connection on the update may have lost only the answer`() {
        assertEquals(PasswordChangeResult.Unconfirmed, passwordUpdateFailure(IOException("timeout")))
    }

    @Test
    fun `any other refusal of the check is a plain failure`() {
        val error = AuthRestException("over_request_rate_limit", "Too many requests", 429)
        assertEquals(PasswordChangeResult.Failed, passwordCheckFailure(error))
    }

    @Test
    fun `a weak new password carries the provider's explanation`() {
        val error = AuthWeakPasswordException("Password should be at least 8 characters.", 422, listOf("length"))
        assertEquals(
            PasswordChangeResult.WeakPassword("Password should be at least 8 characters."),
            passwordUpdateFailure(error),
        )
    }

    @Test
    fun `the provider refusing the same password is reported as such`() {
        val error = AuthRestException("same_password", "New password should be different.", 422)
        assertEquals(PasswordChangeResult.SamePassword, passwordUpdateFailure(error))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, not mapped`() {
        passwordUpdateFailure(CancellationException("left the screen"))
    }
}
