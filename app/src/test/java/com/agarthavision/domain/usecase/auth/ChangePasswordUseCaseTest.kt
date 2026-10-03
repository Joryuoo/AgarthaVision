package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Pins that changing a password is online only and never a local-data operation (14zcqntjph9). */
class ChangePasswordUseCaseTest {

    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock()
    private val useCase = ChangePasswordUseCase(authRepository, connectivityObserver)

    @Test
    fun `offline it answers no connection without trying`() = runTest {
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        assertEquals(PasswordChangeResult.NoConnection, useCase("old-pass", "new-pass"))
        verify(authRepository, never()).changePassword(any(), any())
    }

    @Test
    fun `the same password is refused before any sign-in`() = runTest {
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)

        assertEquals(PasswordChangeResult.SamePassword, useCase("same-pass", "same-pass"))
        verify(authRepository, never()).changePassword(any(), any())
    }

    @Test
    fun `online it hands the provider's answer back unchanged`() = runTest {
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)
        whenever(authRepository.changePassword("old-pass", "new-pass"))
            .thenReturn(PasswordChangeResult.WrongCurrentPassword)

        assertEquals(PasswordChangeResult.WrongCurrentPassword, useCase("old-pass", "new-pass"))
    }
}
