package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.sync.SyncScheduler
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CompleteSignInUseCaseTest {
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase = mock()
    private val syncScheduler: SyncScheduler = mock()
    private val useCase = CompleteSignInUseCase(fetchRemoteDataUseCase, syncScheduler)

    @Test
    fun `pulls patients first then requests the post sign-in sync`() = runTest {
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenReturn(Result.success(3))

        val result = useCase()

        assertTrue(result.isSuccess)
        val order = inOrder(fetchRemoteDataUseCase, syncScheduler)
        order.verify(fetchRemoteDataUseCase).pullPatientsOnly()
        order.verify(syncScheduler).requestSyncAfterSignIn()
    }

    @Test
    fun `a failed pull still requests the sync and reports the failure`() = runTest {
        val boom = IllegalStateException("boom")
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenReturn(Result.failure(boom))

        val result = useCase()

        assertEquals(boom, result.exceptionOrNull())
        verify(syncScheduler).requestSyncAfterSignIn()
    }

    @Test
    fun `a throwing pull does not escape and still requests the sync`() = runTest {
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenThrow(RuntimeException("thrown"))

        val result = useCase()

        assertTrue(result.isFailure)
        verify(syncScheduler).requestSyncAfterSignIn()
    }

    @Test
    fun `a throwing scheduler is folded into a failure and does not escape`() = runTest {
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenReturn(Result.success(3))
        val boom = IllegalStateException("work manager not initialised")
        whenever(syncScheduler.requestSyncAfterSignIn()).thenThrow(boom)

        val result = useCase()

        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `cancellation from the scheduler is rethrown`() = runTest {
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenReturn(Result.success(3))
        whenever(syncScheduler.requestSyncAfterSignIn()).thenThrow(CancellationException("cancelled"))

        try {
            useCase()
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `cancellation is rethrown and requests nothing`() = runTest {
        whenever(fetchRemoteDataUseCase.pullPatientsOnly()).thenThrow(CancellationException("cancelled"))

        try {
            useCase()
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        verify(syncScheduler, never()).requestSyncAfterSignIn()
    }
}
