package com.agarthavision.data.repository

import com.agarthavision.domain.model.AccountAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [accessForRenewalStatus], the rule that decides whether a refused login renewal wipes the
 * phone (14zcqntjph8). A wrong answer here either leaves a leaver's patients on the phone or
 * wipes a working medtech's phone because the server was busy.
 */
class AccountAccessRenewalStatusTest {

    @Test
    fun `the server refusing the renewal is a refusal`() {
        // 400: refresh token gone with a deleted user, or user_banned. 401/403/404: user or
        // session not found.
        listOf(400, 401, 403, 404).forEach { status ->
            assertEquals("HTTP $status", AccountAccess.REFUSED, accessForRenewalStatus(status))
        }
    }

    @Test
    fun `a busy or failing server is not a refusal`() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertEquals("HTTP $status", AccountAccess.UNKNOWN, accessForRenewalStatus(status))
        }
    }
}
