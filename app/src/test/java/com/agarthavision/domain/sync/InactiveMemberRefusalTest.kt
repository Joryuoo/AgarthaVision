package com.agarthavision.domain.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactiveMemberRefusalTest {

    @Test
    fun `the database's reason in the message is a refusal`() {
        val error = IllegalStateException(
            "not_active_lab_member: this account is not an active member of an active laboratory",
        )

        assertTrue(error.isInactiveMemberRefusal())
    }

    @Test
    fun `the reason wrapped in another error is still found`() {
        val error = RuntimeException("upsert failed", IllegalStateException("not_active_lab_member: …"))

        assertTrue(error.isInactiveMemberRefusal())
    }

    @Test
    fun `any other refused write is not one`() {
        val error = IllegalStateException("new row violates row-level security policy for table \"sessions\"")

        assertFalse(error.isInactiveMemberRefusal())
    }

    @Test
    fun `an error without a message is not one`() {
        assertFalse(RuntimeException().isInactiveMemberRefusal())
    }
}
