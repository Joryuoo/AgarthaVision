package com.agarthavision.domain.usecase.inference

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import java.io.IOException

class NetworkErrorMapperTest {

    private val mapper = NetworkErrorMapper()

    @Test
    fun `IOException from block is wrapped as InferenceConnectionException`() = runTest {
        val cause = IOException("network timeout")
        val ex = runCatching {
            mapper.execute { throw cause }
        }.exceptionOrNull()

        assertTrue(ex is InferenceConnectionException)
        assertSame(cause, ex?.cause)
    }

    @Test
    fun `HttpException with 5xx is wrapped as InferenceConnectionException`() = runTest {
        val httpEx: HttpException = mock()
        whenever(httpEx.code()).thenReturn(503)

        val ex = runCatching {
            mapper.execute<Unit> { throw httpEx }
        }.exceptionOrNull()

        assertTrue(ex is InferenceConnectionException)
        assertSame(httpEx, ex?.cause)
    }

    @Test
    fun `HttpException with 4xx is rethrown as-is`() = runTest {
        val httpEx: HttpException = mock()
        whenever(httpEx.code()).thenReturn(401)

        val ex = runCatching {
            mapper.execute<Unit> { throw httpEx }
        }.exceptionOrNull()

        assertSame(httpEx, ex)
    }

    @Test
    fun `successful block result is returned unchanged`() = runTest {
        val result = mapper.execute { 42 }
        assertEquals(42, result)
    }
}
