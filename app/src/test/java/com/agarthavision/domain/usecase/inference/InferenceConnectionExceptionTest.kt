package com.agarthavision.domain.usecase.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class InferenceConnectionExceptionTest {

    @Test
    fun `default message is set`() {
        val ex = InferenceConnectionException()
        assertEquals("Inference container unreachable", ex.message)
    }

    @Test
    fun `cause is null when not provided`() {
        val ex = InferenceConnectionException()
        assertNull(ex.cause)
    }

    @Test
    fun `cause is preserved when provided`() {
        val root = IOException("network failure")
        val ex = InferenceConnectionException(cause = root)
        assertSame(root, ex.cause)
    }

    @Test
    fun `message is set even when cause is provided`() {
        val ex = InferenceConnectionException(cause = RuntimeException("boom"))
        assertEquals("Inference container unreachable", ex.message)
    }

    @Test
    fun `is a subtype of Exception`() {
        val ex = InferenceConnectionException()
        assertTrue(ex is Exception)
    }

    @Test
    fun `can be caught as Exception`() {
        var caught = false
        try {
            throw InferenceConnectionException()
        } catch (e: Exception) {
            caught = true
        }
        assertTrue(caught)
    }
}
